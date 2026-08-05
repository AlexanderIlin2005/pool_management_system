package ru.sashil.bot.commands

import ru.sashil.bot.util.CommandUtils
import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.DateUtils
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class EditChildCommand(
    private val dbService: DatabaseService
) : BotCommand {
    override val displayName: String = "Редактировать ребенка"
    override val description: String = "Изменение данных ребенка"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, String>>()
    private val editingChildId = ConcurrentHashMap<Long, Long>()

    override fun start(userId: Long): CommandResult {
        val isRegistered = try {
            dbService.isParentRegistered(userId)
        } catch (e: Exception) {
            false
        }
        if (!isRegistered) {
            return CommandResult.Complete("⚠️ Вы не зарегистрированы в системе.\nНапишите 'меню' для регистрации.")
        }

        val children = try {
            dbService.getChildrenByParentVkId(userId)
        } catch (e: Exception) {
            return CommandResult.Error("Ошибка получения списка детей: ${e.message}")
        }

        if (children.isEmpty()) {
            return CommandResult.Complete("У Вас пока нет зарегистрированных детей.\nНапишите 'меню' для регистрации ребенка.")
        }

        userSteps[userId] = 1
        val data = mutableMapOf<String, String>()
        userData[userId] = data

        if (children.size == 1) {
            val child = children[0]
            val childId = (child["id"] as Number).toLong()
            editingChildId[userId] = childId

            data["childName"] = "${child["lastName"]} ${child["firstName"]}"
            data["lastName"] = child["lastName"] as? String ?: ""
            data["firstName"] = child["firstName"] as? String ?: ""
            data["middleName"] = child["middleName"] as? String ?: ""
            data["birthDate"] = child["birthDate"] as? String ?: ""
            data["gradeNumber"] = child["gradeNumber"]?.toString() ?: "0"
            data["gradeName"] = child["gradeName"] as? String ?: ""
            data["skill"] = child["skill"] as? String ?: ""

            return CommandResult.Continue(
                "Редактирование ребенка: ${data["childName"]}\n" +
                        "Введите новую фамилию (или '-'(тире/минус) для пропуска):"
            )
        } else {
            val sb = StringBuilder("Выберите ребенка для редактирования:\n")
            children.forEachIndexed { i, child ->
                val name = "${child["lastName"]} ${child["firstName"]}"
                val middleName = child["middleName"] as? String
                if (!middleName.isNullOrEmpty()) {
                    sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]} $middleName\n")
                } else {
                    sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]}\n")
                }
            }
            return CommandResult.Continue(sb.toString())
        }
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = userSteps[userId] ?: return CommandResult.Error("Сессия не найдена")
        val data = userData[userId] ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (CommandUtils.isCancelCommand(text)) {
            userSteps.remove(userId)
            userData.remove(userId)
            editingChildId.remove(userId)
            return CommandResult.Cancel()
        }

        if (!editingChildId.containsKey(userId) && step == 1) {
            val children = try {
                dbService.getChildrenByParentVkId(userId)
            } catch (e: Exception) {
                return CommandResult.Error("Ошибка получения списка детей: ${e.message}")
            }
            val num = text.trim().toIntOrNull()
            if (num == null || num < 1 || num > children.size) {
                return CommandResult.Continue("Пожалуйста, введите номер ребенка от 1 до ${children.size}.")
            }
            val child = children[num - 1]
            val childId = (child["id"] as Number).toLong()
            editingChildId[userId] = childId

            data["childName"] = "${child["lastName"]} ${child["firstName"]}"
            data["lastName"] = child["lastName"] as? String ?: ""
            data["firstName"] = child["firstName"] as? String ?: ""
            data["middleName"] = child["middleName"] as? String ?: ""
            data["birthDate"] = child["birthDate"] as? String ?: ""
            data["gradeNumber"] = child["gradeNumber"]?.toString() ?: "0"
            data["gradeName"] = child["gradeName"] as? String ?: ""
            data["skill"] = child["skill"] as? String ?: ""

            userSteps[userId] = 2
            return CommandResult.Continue(
                "Редактирование ребенка: ${data["childName"]}\n" +
                        "Введите новую фамилию (или '-'(тире/минус) для пропуска):"
            )
        }

        when (step) {
            1 -> {
                return CommandResult.Continue("Пожалуйста, введите номер ребенка.")
            }
            2 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    if (text.trim().length < 2) return CommandResult.Continue("Фамилия должна содержать минимум 2 символа.")
                    data["lastName"] = text.trim()
                }
                userSteps[userId] = 3
                return CommandResult.Continue("Введите новое имя (или '-'(тире/минус) для пропуска):")
            }
            3 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    if (text.trim().length < 2) return CommandResult.Continue("Имя должно содержать минимум 2 символа.")
                    data["firstName"] = text.trim()
                }
                userSteps[userId] = 4
                return CommandResult.Continue("Введите новое отчество (или '-'(тире/минус) для пропуска):")
            }
            4 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    data["middleName"] = text.trim()
                }
                userSteps[userId] = 5
                return CommandResult.Continue("Введите новую дату рождения (ДД.ММ.ГГГГ) или '-'(тире/минус) для пропуска:")
            }
            5 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    val sqlDate = DateUtils.normalizeDate(text)
                    if (sqlDate == null) {
                        return CommandResult.Continue("Неверный формат даты. Используйте ДД.ММ.ГГГГ")
                    }
                    if (LocalDate.parse(sqlDate).isAfter(LocalDate.now())) {
                        return CommandResult.Continue("Дата не может быть в будущем.")
                    }
                    data["birthDate"] = sqlDate
                }
                userSteps[userId] = 6
                return CommandResult.Continue("Введите новый номер класса (1-11) или '-'(тире/минус) для пропуска:")
            }
            6 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    try {
                        val grade = text.trim().toInt()
                        if (grade !in 1..11) return CommandResult.Continue("Класс должен быть от 1 до 11.")
                        data["gradeNumber"] = grade.toString()
                    } catch (e: NumberFormatException) {
                        return CommandResult.Continue("Введите число от 1 до 11.")
                    }
                }
                userSteps[userId] = 7
                return CommandResult.Continue("Введите полное название класса (или '-'(тире/минус) для пропуска):")
            }
            7 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    data["gradeName"] = text.trim()
                }
                userSteps[userId] = 8
                return CommandResult.Continue(
                    "Выберите навык плавания:\n" +
                            "1. Уверенно плавает\n" +
                            "2. Держится на воде\n" +
                            "3. Не умеет плавать\n" +
                            "(Введите номер или '-'(тире/минус) для пропуска)"
                )
            }
            8 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    val skill = when (text.trim()) {
                        "1" -> "уверенно плавает"
                        "2" -> "держится на воде"
                        "3" -> "не умеет"
                        else -> return CommandResult.Continue("Введите номер от 1 до 3.")
                    }
                    data["skill"] = skill
                }
                userSteps[userId] = 9
                return CommandResult.Continue(
                    "Проверьте введенные данные:\n" +
                            "ФИО: ${data["lastName"]} ${data["firstName"]} ${data["middleName"]}\n" +
                            "Дата рождения: ${formatDate(data["birthDate"].orEmpty())}\n" +
                            "Класс: ${data["gradeName"]} (${data["gradeNumber"]})\n" +
                            "Навык: ${data["skill"]}\n" +
                            "Всё верно?\n" +
                            "Напишите 'да' для сохранения или 'нет' для отмены."
                )
            }
            9 -> {
                if (cmd != "да") {
                    userSteps.remove(userId)
                    userData.remove(userId)
                    editingChildId.remove(userId)
                    return CommandResult.Cancel("Редактирование отменено.")
                }
                try {
                    val childId = editingChildId[userId] ?: return CommandResult.Error("Ребенок не найден")

                    val firstName = data["firstName"].orEmpty()
                    val lastName = data["lastName"].orEmpty()
                    val middleName = data["middleName"].orEmpty()
                    val birthDate = data["birthDate"].orEmpty()
                    val gradeNumber = data["gradeNumber"]?.toIntOrNull() ?: 0
                    val gradeName = data["gradeName"].orEmpty()
                    val skill = data["skill"].orEmpty()

                    dbService.updateChild(
                        childId,
                        firstName,
                        lastName,
                        middleName,
                        birthDate,
                        gradeNumber,
                        gradeName,
                        skill
                    )

                    userSteps.remove(userId)
                    userData.remove(userId)
                    editingChildId.remove(userId)
                    return CommandResult.Complete("✅ Данные ребенка успешно обновлены!")
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка сохранения: ${e.message}")
                }
            }
            else -> {
                return CommandResult.Cancel()
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        try {
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                return "${parts[2]}.${parts[1]}.${parts[0]}"
            }
        } catch (_: Exception) {}
        return dateStr
    }

    override fun cancel(userId: Long): CommandResult {
        userSteps.remove(userId)
        userData.remove(userId)
        editingChildId.remove(userId)
        return CommandResult.Cancel()
    }
}