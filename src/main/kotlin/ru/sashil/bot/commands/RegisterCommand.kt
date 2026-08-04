package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.CommandUtils
import java.util.concurrent.ConcurrentHashMap

class RegisterCommand(
    private val dbService: DatabaseService
) : BotCommand {

    override val displayName: String = "Зарегистрировать ребенка в бассейн"
    override val description: String = "Регистрация нового ребенка для занятий"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, String>>()

    override fun start(userId: Long): CommandResult {
        // Проверяем, зарегистрирован ли родитель
        val isParentRegistered = try {
            dbService.isParentRegistered(userId)
        } catch (e: Exception) {
            false
        }

        if (!isParentRegistered) {
            return CommandResult.Complete(
                "⚠️ Для регистрации ребенка необходимо сначала зарегистрироваться как родитель.\n\n" +
                        "Напишите 'начать' для регистрации."
            )
        }

        userSteps[userId] = 1
        userData[userId] = mutableMapOf()
        return CommandResult.Continue(
            "Вы хотите зарегистрировать ребенка для занятий в бассейне гимназии №642 «Земля и Вселенная» по адресу Морская набережная, 5?\n\n(Да/Нет)"
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
                                "Вы хотите зарегистрировать ребенка для занятий в бассейне гимназии №642 «Земля и Вселенная» по адресу Морская набережная, 5?"
                    )
                }
                userSteps[userId] = 2
                return CommandResult.Continue(
                    "Для регистрации Вашего ребенка и дальнейшего выбора группы напишите, пожалуйста, Фамилию Имя Отчество ребенка в такой последовательности.\n\n(Иванов Иван Иванович)"
                )
            }
            2 -> {
                val parts = text.trim().split("\\s+".toRegex())
                if (parts.size < 3) {
                    return CommandResult.Continue(
                        "Пожалуйста, введите полные Фамилию, Имя и Отчество через пробел.\n\n(Иванов Иван Иванович)"
                    )
                }
                data["lastName"] = parts[0]
                data["firstName"] = parts[1]
                data["middleName"] = parts.drop(2).joinToString(" ")
                userSteps[userId] = 3
                return CommandResult.Continue("Спасибо! Введите дату рождения ребенка.\n\n(31.10.2015)")
            }
            3 -> {
                val birthDate = parseDate(text)
                if (birthDate == null) {
                    return CommandResult.Continue("Неверный формат даты. Используйте ДД.ММ.ГГГГ\n\n(31.10.2015)")
                }
                data["birthDate"] = birthDate
                userSteps[userId] = 4
                return CommandResult.Continue(
                    "Спасибо! Укажите класс на текущий учебный год (Пример: 2-3, 5 ЕН, 3 гамма, 10А)\n\n(5 ЕН)"
                )
            }
            4 -> {
                data["gradeName"] = text.trim()
                val gradeNumber = text.trim().replace(Regex("\\D.*"), "").toIntOrNull()
                if (gradeNumber != null && gradeNumber in 1..11) {
                    data["gradeNumber"] = gradeNumber.toString()
                } else {
                    data["gradeNumber"] = "0"
                }
                userSteps[userId] = 5
                return CommandResult.Continue(
                    "Спасибо! Уточните уровень владения плавательными навыками (напишите цифру): \n" +
                            "1. Уверенно плавает\n" +
                            "2. Держится на воде\n" +
                            "3. Не умеет плавать"
                )
            }
            5 -> {
                val skill = when (text.trim()) {
                    "1" -> "уверенно плавает"
                    "2" -> "держится на воде"
                    "3" -> "не умеет"
                    else -> {
                        return CommandResult.Continue(
                            "Пожалуйста, введите цифру от 1 до 3.\n" +
                                    "1. Уверенно плавает\n" +
                                    "2. Держится на воде\n" +
                                    "3. Не умеет плавать"
                        )
                    }
                }
                data["skill"] = skill

                // Показываем подтверждение
                val lastName = data["lastName"] ?: ""
                val firstName = data["firstName"] ?: ""
                val middleName = data["middleName"] ?: ""
                val birthDate = data["birthDate"] ?: ""
                val gradeName = data["gradeName"] ?: ""
                val gradeNumber = data["gradeNumber"] ?: ""

                userSteps[userId] = 6
                return CommandResult.Continue(
                    "Проверьте введенные данные:\n\n" +
                            "ФИО: $lastName $firstName $middleName\n" +
                            "Дата рождения: ${formatDate(birthDate)}\n" +
                            "Класс: $gradeName ($gradeNumber)\n" +
                            "Навык: $skill\n\n" +
                            "Всё верно?\n" +
                            "Напишите 'да' для сохранения или 'нет' для отмены."
                )
            }
            6 -> {
                if (cmd != "да") {
                    userSteps.remove(userId)
                    userData.remove(userId)
                    return CommandResult.Cancel("Регистрация ребенка отменена.")
                }

                try {
                    dbService.addChild(
                        userId,
                        data["firstName"]!!,
                        data["lastName"]!!,
                        data["middleName"],
                        data["birthDate"]!!,
                        data["gradeNumber"]!!.toInt(),
                        data["gradeName"]!!,
                        data["skill"]!!
                    )
                    userSteps.remove(userId)
                    userData.remove(userId)
                    return CommandResult.Complete(
                        "✅ Ваш ребенок успешно зарегистрирован для занятий в бассейне.\n\n" +
                                "Напишите 'меню' для просмотра доступных действий."
                    )
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка сохранения данных: ${e.message}")
                }
            }
            else -> {
                return CommandResult.Cancel()
            }
        }
    }

    private fun parseDate(text: String): String? {
        val parts = text.trim().split("\\D".toRegex()).filter { it.isNotEmpty() }
        if (parts.size != 3) return null
        try {
            val day = parts[0].toInt()
            val month = parts[1].toInt()
            val year = parts[2].toInt()
            if (day in 1..31 && month in 1..12 && year in 1900..2100) {
                return String.format("%04d-%02d-%02d", year, month, day)
            }
        } catch (_: Exception) {}
        return null
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
        return CommandResult.Cancel()
    }
}