package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.CommandUtils
import java.util.concurrent.ConcurrentHashMap

class SelectGroupCommand(
    private val dbService: DatabaseService
) : BotCommand {

    override val displayName: String = "Выбрать группу для занятий в бассейне"
    override val description: String = "Подбор группы по возрасту и навыкам"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, Any>>()

    private val subscriptionTypeMap = mapOf(
        "1" to "ONCE_PER_WEEK",
        "2" to "TWICE_PER_WEEK",
        "3" to "INDIVIDUAL",
        "4" to "FAMILY",
        "5" to "AQUA_AEROBICS"
    )

    override fun start(userId: Long): CommandResult {
        userSteps[userId] = 1
        userData[userId] = mutableMapOf()
        return CommandResult.Continue(
            "Вы хотите выбрать группу для занятий в бассейне?\n\n(Да/Нет)"
        )
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = userSteps[userId] ?: return CommandResult.Error("Сессия не найдена")
        val data = userData[userId] ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (cmd == "нет" || cmd == "отмена") {
            if (step == 1) {
                return CommandResult.Cancel()
            } else {
                return CommandResult.Cancel()
            }
        }

        when (step) {
            1 -> {
                if (cmd != "да") {
                    return CommandResult.Continue(
                        "Для возврата в главное меню напишите 'нет'.\n\n" +
                        "Вы хотите выбрать группу для занятий в бассейне?"
                    )
                }

                val children = try {
                    dbService.getChildrenByParentVkId(userId)
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка получения данных: ${e.message}")
                }

                if (children.isEmpty()) {
                    return CommandResult.Continue(
                        "У Вас пока нет зарегистрированных детей. Сначала зарегистрируйте ребенка (команда 1)."
                    )
                }

                data["children"] = children

                if (children.size == 1) {
                    val child = children[0]
                    data["childId"] = (child["id"] as Number).toLong()
                    data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
                    userSteps[userId] = 3
                    return showSubscriptionTypes()
                } else {
                    userSteps[userId] = 2
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
            2 -> {
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
                userSteps[userId] = 3
                return showSubscriptionTypes()
            }
            3 -> {
                val subType = subscriptionTypeMap[text.trim()]
                if (subType == null) {
                    return CommandResult.Continue(
                        "Пожалуйста, введите цифру от 1 до 5:\n" +
                        "1. Занятия в бассейне 1 раз в неделю\n" +
                        "2. Занятия в бассейне 2 раза в неделю\n" +
                        "3. Индивидуальные занятия с тренером\n" +
                        "4. Семейное плавание\n" +
                        "5. Аквааэробика"
                    )
                }
                data["subscriptionType"] = subType
                userSteps[userId] = 4
                return findSuitableGroups(userId, data)
            }
            4 -> {
                val groups = data["groups"] as? List<Map<String, Any>> ?: return CommandResult.Error("Группы не найдены")
                val num = text.trim().toIntOrNull()
                if (num == null || num < 1 || num > groups.size) {
                    return CommandResult.Continue(
                        "Пожалуйста, введите номер группы от 1 до ${groups.size}."
                    )
                }

                val selectedGroup = groups[num - 1]
                val groupId = (selectedGroup["id"] as Number).toLong()
                val childId = data["childId"] as Long

                try {
                    dbService.createJoinRequest(userId, childId, groupId)
                    userSteps.remove(userId)
                    userData.remove(userId)
                    return CommandResult.Complete(
                        "Благодарим! Вы успешно зарегистрировали ребенка в бассейн и выбрали группу для занятий плаванием.\n\n" +
                        "В ближайшее время Вы получите уведомление от администратора о зачислении в группу. Вам будут направлены дальнейшие инструкции.\n\n" +
                        "Просим ознакомиться с текстом договора на оказание услуг, с Правилами посещения бассейна, с текстом Согласия на обработку персональных данных. Эти документы размещены в ВК группе бассейна.\n\n" +
                        "Будем рады видеть Вашего ребенка на занятиях в бассейне!"
                    )
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка создания заявки: ${e.message}")
                }
            }
            else -> {
                return CommandResult.Cancel()
            }
        }
    }

    private fun showSubscriptionTypes(): CommandResult {
        return CommandResult.Continue(
            "Спасибо! Уточните, какой абонемент Вы бы хотели выбрать:\n" +
            "1. Занятия в бассейне 1 раз в неделю\n" +
            "2. Занятия в бассейне 2 раза в неделю\n" +
            "3. Индивидуальные занятия с тренером\n" +
            "4. Семейное плавание\n" +
            "5. Аквааэробика"
        )
    }

    private fun findSuitableGroups(userId: Long, data: MutableMap<String, Any>): CommandResult {
        val childId = data["childId"] as Long
        val subscriptionType = data["subscriptionType"] as String

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

        val gradeName = childData["gradeName"] as String

        val groups = try {
            dbService.findSuitableGroupsForChild(childId)
        } catch (e: Exception) {
            return CommandResult.Error("Ошибка поиска групп: ${e.message}")
        }

        if (groups.isEmpty()) {
            userSteps.remove(userId)
            userData.remove(userId)
            return CommandResult.Complete(
                "К сожалению, сейчас нет подходящих групп для Вашего ребенка. " +
                        "Возможно, он уже состоит во всех подходящих группах.\n\n" +
                        "Пожалуйста, свяжитесь с администратором через команду 6."
            )
        }

        // Фильтруем по типу абонемента
        // Если у группы subscription_type = null - она подходит для любого типа
        // Если у группы subscription_type совпадает с выбранным - подходит
        val filteredGroups = groups.filter { group ->
            val type = group["subscription_type"] as? String
            type == null || type == subscriptionType
        }

        if (filteredGroups.isEmpty()) {
            userSteps.remove(userId)
            userData.remove(userId)
            return CommandResult.Complete(
                "К сожалению, нет групп с выбранным типом абонемента (${getSubscriptionTypeDisplay(subscriptionType)}).\n\n" +
                        "Попробуйте выбрать другой тип абонемента или свяжитесь с администратором через команду 6."
            )
        }

        data["groups"] = filteredGroups

        val sb = StringBuilder()
        sb.append("Вашему ребенку ($fullChildName, $age лет, класс $gradeName, $skill) по возрасту и уровню умения плавать подходят следующие группы:\n\n")

        filteredGroups.forEachIndexed { index, group ->
            val groupName = group["name"] as String
            val groupNumber = group["number"] as Int
            val schedule = getGroupSchedule(group)
            val typeDisplay = when (group["subscription_type"] as? String) {
                "ONCE_PER_WEEK" -> "1 раз в неделю"
                "TWICE_PER_WEEK" -> "2 раза в неделю"
                "INDIVIDUAL" -> "Индивидуальные занятия с тренером"
                "FAMILY" -> "Семейное плавание"
                "AQUA_AEROBICS" -> "Аквааэробика"
                else -> ""
            }
            sb.append("${index + 1}. $groupName")
            if (typeDisplay.isNotEmpty()) sb.append(" ($typeDisplay)")
            sb.append(": $schedule\n")
        }

        sb.append("\nВыберите, пожалуйста, группу. Напишите только цифру.")
        userSteps[userId] = 4
        return CommandResult.Continue(sb.toString())
    }

    private fun getSubscriptionTypeDisplay(type: String): String {
        return when (type) {
            "ONCE_PER_WEEK" -> "1 раз в неделю"
            "TWICE_PER_WEEK" -> "2 раза в неделю"
            "INDIVIDUAL" -> "Индивидуальные занятия с тренером"
            "FAMILY" -> "Семейное плавание"
            "AQUA_AEROBICS" -> "Аквааэробика"
            else -> type
        }
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
}
