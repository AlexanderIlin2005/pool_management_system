package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.CommandUtils
import ru.sashil.common.util.NameUtils
import java.util.regex.Pattern

class SelectGroupCommand(
    private val dbService: DatabaseService
) : BaseBotCommand() {

    override val displayName: String = "Выбрать группу для занятий в бассейне"
    override val description: String = "Подбор группы по возрасту и навыкам"

    private val adminCommandNumber = BotCommandType.MESSAGE_ADMIN.getCommandNumber()

    override fun start(userId: Long): CommandResult {
        setStep(userId, 1)
        val data = createData(userId)

        val children = try {
            dbService.getChildrenByParentVkId(userId)
        } catch (e: Exception) {
            return CommandResult.Error("Ошибка получения данных: ${e.message}")
        }

        if (children.isEmpty()) {
            removeSession(userId)
            return CommandResult.Complete(
                "У Вас пока нет зарегистрированных детей. Сначала зарегистрируйте ребенка (команда ${BotCommandType.REGISTER_CHILD.getCommandNumber()})."
            )
        }

        data["children"] = children

        if (children.size == 1) {
            val child = children[0]
            data["childId"] = (child["id"] as Number).toLong()
            data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
            setStep(userId, 2)
            return showSubscriptionTypes()
        } else {
            setStep(userId, 1)
            val sb = StringBuilder("Выберите ребенка, для которого Вы хотите выбрать группу:\n\n")
            children.forEachIndexed { i, child ->
                val name = (child["lastName"] as String) + " " + (child["firstName"] as String)
                if (child["middleName"] != null && (child["middleName"] as String).isNotEmpty()) {
                    sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]} ${child["middleName"]}\n")
                } else {
                    sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]}\n")
                }
            }
            return CommandResult.Continue(sb.toString())
        }
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = getStep(userId)
        val data = getData(userId) ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (cmd == "нет" || cmd == "отмена") {
            return CommandResult.Cancel()
        }

        when (step) {
            1 -> {
                val children = data["children"] as? List<Map<String, Any>> ?: return CommandResult.Error("Ошибка данных")
                val num = text.trim().toIntOrNull()
                if (num == null || num < 1 || num > children.size) {
                    return CommandResult.Continue(
                        "Пожалуйста, введите номер ребенка от 1 до ${children.size}."
                    )
                }
                val child = children[num - 1]
                data["childId"] = (child["id"] as Number).toLong()
                data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
                setStep(userId, 2)
                return showSubscriptionTypes()
            }
            2 -> {
                val types = try {
                    dbService.getAllSubscriptionTypes()
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка загрузки типов занятий: ${e.message}")
                }
                if (types.isEmpty()) {
                    return CommandResult.Error("Нет доступных типов занятий.")
                }

                val num = text.trim().toIntOrNull()
                if (num == null || num < 1 || num > types.size) {
                    val sb = StringBuilder("Выберите тип занятия:\n\n")
                    types.forEachIndexed { i, t ->
                        sb.append("${i + 1}. ${t["display_name"]}\n")
                    }
                    return CommandResult.Continue(sb.toString())
                }
                data["subscriptionTypeId"] = (types[num - 1]["id"] as Number).toLong()
                setStep(userId, 3)
                return findSuitableGroups(userId, data)
            }
            3 -> {
                val groups = data["groups"] as? List<Map<String, Any>> ?: return CommandResult.Error("Группы не найдены")
                val groupCount = groups.size

                val selectedIndexes = parseGroupSelection(text.trim(), groupCount)

                if (selectedIndexes.isEmpty()) {
                    return CommandResult.Continue(
                        "Пожалуйста, введите номер группы (или несколько номеров через пробел, запятую или 'и').\n" +
                                "Доступные номера: от 1 до $groupCount."
                    )
                }

                val childId = data["childId"] as Long
                var successCount = 0
                var errorMessages = mutableListOf<String>()

                for (idx in selectedIndexes) {
                    try {
                        val selectedGroup = groups[idx - 1]
                        val groupId = (selectedGroup["id"] as Number).toLong()

                        dbService.createJoinRequest(userId, childId, groupId)
                        successCount++
                    } catch (e: Exception) {
                        errorMessages.add("Ошибка при создании заявки для группы #${idx}: ${e.message}")
                    }
                }

                removeSession(userId)

                if (successCount == 0) {
                    return CommandResult.Error(
                        "Не удалось создать ни одной заявки.\n" +
                                errorMessages.joinToString("\n")
                    )
                }

                val message = StringBuilder()
                message.append("Благодарим! Вы успешно создали заявку")
                if (successCount > 1) message.append("и")
                message.append(" на вступление в группу")
                if (successCount > 1) message.append("ы")
                message.append("!\n\n")

                if (successCount == 1) {
                    message.append("В ближайшее время Вы получите уведомление от администратора о зачислении в группу.\n\n")
                } else {
                    message.append("В ближайшее время Вы получите уведомления от администратора о зачислении в каждую из выбранных групп.\n\n")
                }

                message.append(
                    "Просим ознакомиться с текстом договора на оказание услуг, с Правилами посещения бассейна, с текстом Согласия на обработку персональных данных. " +
                            "Эти документы размещены в ВК группе бассейна.\n\n" +
                            "Будем рады видеть Вашего ребенка на занятиях в бассейне!"
                )

                if (errorMessages.isNotEmpty()) {
                    message.append("\n\n⚠️ Частичные ошибки:\n")
                    message.append(errorMessages.joinToString("\n"))
                }

                return CommandResult.Complete(message.toString())
            }
            else -> {
                return CommandResult.Cancel()
            }
        }
    }

    private fun showSubscriptionTypes(): CommandResult {
        val types = try {
            dbService.getAllSubscriptionTypes()
        } catch (e: Exception) {
            return CommandResult.Error("Ошибка загрузки типов занятий: ${e.message}")
        }
        if (types.isEmpty()) {
            return CommandResult.Error("Нет доступных типов занятий.")
        }
        val sb = StringBuilder("Выберите тип занятия:\n\n")
        types.forEachIndexed { i, t ->
            sb.append("${i + 1}. ${t["display_name"]}\n")
        }
        return CommandResult.Continue(sb.toString())
    }

    private fun findSuitableGroups(userId: Long, data: MutableMap<String, Any>): CommandResult {
        val childId = data["childId"] as Long
        val selectedSubTypeId = data["subscriptionTypeId"] as Long

        val childData = try {
            val children = dbService.getChildrenByParentVkId(userId)
            children.find { (it["id"] as Number).toLong() == childId }
        } catch (e: Exception) {
            return CommandResult.Error("Ошибка получения данных ребенка: ${e.message}")
        }

        if (childData == null) {
            return CommandResult.Error("Ребенок не найден")
        }

        val age = childData["age"] as Int
        val skill = childData["skill"] as String
        val childName = "${childData["lastName"]} ${childData["firstName"]}"
        val middleName = childData["middleName"] as? String
        val fullChildName = if (middleName != null && middleName.isNotEmpty()) {
            "$childName $middleName"
        } else {
            childName
        }

        val gradeName = childData["gradeName"] as? String ?: "—"

        val groups = try {
            dbService.findSuitableGroupsForChild(childId)
        } catch (e: Exception) {
            return CommandResult.Error("Ошибка поиска групп: ${e.message}")
        }

        if (groups.isEmpty()) {
            removeSession(userId)
            return CommandResult.Complete(
                "К сожалению, сейчас нет подходящих групп для Вашего ребенка. " +
                        "Возможно, он уже состоит во всех подходящих группах.\n\n" +
                        "Пожалуйста, свяжитесь с администратором через команду $adminCommandNumber."
            )
        }

        val filteredGroups = groups.filter { group ->
            val subTypeId = group["subscription_type_id"] as? Long
            subTypeId == null || subTypeId == selectedSubTypeId
        }

        if (filteredGroups.isEmpty()) {
            val subTypeName = try {
                val types = dbService.getAllSubscriptionTypes()
                types.find { (it["id"] as Number).toLong() == selectedSubTypeId }
                    ?.get("display_name") as? String ?: "выбранный тип"
            } catch (e: Exception) {
                "выбранный тип"
            }

            removeSession(userId)
            return CommandResult.Complete(
                "К сожалению, нет групп с выбранным типом занятия ($subTypeName).\n\n" +
                        "Попробуйте выбрать другой тип занятия или свяжитесь с администратором через команду $adminCommandNumber."
            )
        }

        data["groups"] = filteredGroups

        val sb = StringBuilder()
        val gradeDisplay = if (gradeName.isNotEmpty() && gradeName != "—") {
            ", класс $gradeName"
        } else {
            ""
        }
        sb.append("Вашему ребенку ($fullChildName, $age лет$gradeDisplay, $skill) по возрасту и уровню умения плавать подходят следующие группы:\n\n")

        filteredGroups.forEachIndexed { index, group ->
            val schedule = getGroupSchedule(group)
            val trainerFullName = group["trainer_full_name"] as? String
            val trainerInitials = if (trainerFullName != null) {
                NameUtils.toInitials(trainerFullName)
            } else {
                "тренер не назначен"
            }

            sb.append("${index + 1}. $schedule — $trainerInitials\n")
        }

        sb.append("\nВыберите одну или несколько групп (например: 3, 5 или 3 и 5). Напишите только цифры.")
        setStep(userId, 3)
        return CommandResult.Continue(sb.toString())
    }

    private fun getGroupSchedule(group: Map<String, Any>): String {
        val days = listOf(
            "Пн" to Pair(group["day_1_start"], group["day_1_end"]),
            "Вт" to Pair(group["day_2_start"], group["day_2_end"]),
            "Ср" to Pair(group["day_3_start"], group["day_3_end"]),
            "Чт" to Pair(group["day_4_start"], group["day_4_end"]),
            "Пт" to Pair(group["day_5_start"], group["day_5_end"]),
            "Сб" to Pair(group["day_6_start"], group["day_6_end"]),
            "Вс" to Pair(group["day_7_start"], group["day_7_end"])
        )

        val schedule = days.mapNotNull { (dayName, times) ->
            val start = times.first
            val end = times.second
            if (start != null && end != null) {
                "$dayName ${formatTime(start)}-${formatTime(end)}"
            } else null
        }

        return if (schedule.isNotEmpty()) schedule.joinToString(", ") else "расписание не указано"
    }

    private fun formatTime(time: Any): String {
        return time.toString().substring(0, 5)
    }

    private fun parseGroupSelection(input: String, maxIndex: Int): List<Int> {
        if (input.isBlank()) return emptyList()

        val separators = arrayOf("\\s+", "\\s*,\\s*", "\\s+и\\s+", "\\s+and\\s+")
        val pattern = Pattern.compile(separators.joinToString("|"))

        val parts = pattern.split(input.trim()).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()

        val result = mutableListOf<Int>()
        for (part in parts) {
            val num = part.toIntOrNull()
            if (num == null || num < 1 || num > maxIndex) {
                return emptyList()
            }
            result.add(num)
        }

        return result.distinct()
    }

    override fun cancel(userId: Long): CommandResult {
        removeSession(userId)
        return CommandResult.Cancel()
    }
}
