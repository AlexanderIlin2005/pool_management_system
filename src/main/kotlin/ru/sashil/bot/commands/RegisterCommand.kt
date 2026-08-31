package ru.sashil.bot.commands

import ru.sashil.bot.util.CommandUtils
import ru.sashil.common.service.DatabaseService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class RegisterCommand(
    private val dbService: DatabaseService
) : BotCommand {

    override val displayName: String = "Зарегистрировать ребенка в бассейн"
    override val description: String = "Регистрация нового ребенка для занятий"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, String>>()

    override fun start(userId: Long): CommandResult {
        val isParentRegistered = try {
            dbService.isParentRegistered(userId)
        } catch (e: Exception) {
            false
        }

        if (!isParentRegistered) {
            return CommandResult.Complete(
                "⚠️ Для регистрации ребенка необходимо сначала зарегистрироваться как родитель.\n\n" +
                        "Напишите 'меню' для регистрации."
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

        if (CommandUtils.isCancelCommand(text)) {
            userSteps.remove(userId)
            userData.remove(userId)
            return CommandResult.Cancel()
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
                    "Для регистрации Вашего ребенка напишите, пожалуйста, Фамилию Имя Отчество ребенка в такой последовательности.\n\n(Иванов Иван Иванович)"
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
                return CommandResult.Continue("Спасибо! Введите дату рождения ребенка.\n\n(Например 31.10.2015)")
            }
            3 -> {
                val birthDateStr = text.trim()
                val birthDate = parseDate(birthDateStr)
                if (birthDate == null) {
                    return CommandResult.Continue("Неверный формат даты. Используйте ДД.ММ.ГГГГ")
                }
                data["birthDate"] = birthDate

                // Вычисляем возраст
                val age = calculateAge(birthDate)
                data["age"] = age.toString()

                // Проверяем возраст
                if (age < 6) {
                    // Дошкольник — пропускаем класс
                    data["gradeNumber"] = "0"  // 0 означает "без класса"
                    data["gradeName"] = ""
                    userSteps[userId] = 6  // Сразу переходим к навыку
                    return CommandResult.Continue(
                        "✅ Возраст ребенка ($age лет) меньше 6 лет, поэтому мы пропускаем шаг с вводом класса.\n\n" +
                                "Уточните уровень владения плавательными навыками (напишите цифру): \n" +
                                "1. Уверенно плавает\n" +
                                "2. Держится на воде\n" +
                                "3. Не умеет плавать"
                    )
                } else if (age > 18) {
                    // Взрослый (для семейных групп) — пропускаем класс
                    data["gradeNumber"] = "0"  // 0 означает "без класса"
                    data["gradeName"] = ""
                    userSteps[userId] = 6  // Сразу переходим к навыку
                    return CommandResult.Continue(
                        "✅ Возраст ребенка ($age лет) больше 18 лет, поэтому мы пропускаем шаг с вводом класса.\n\n" +
                                "Уточните уровень владения плавательными навыками (напишите цифру): \n" +
                                "1. Уверенно плавает\n" +
                                "2. Держится на воде\n" +
                                "3. Не умеет плавать"
                    )
                }

                userSteps[userId] = 4
                return CommandResult.Continue(
                    "Спасибо! Введите номер класса (цифрой от 1 до 11):"
                )
            }
            4 -> {
                try {
                    val gradeNumber = text.trim().toInt()
                    if (gradeNumber !in 1..11) {
                        return CommandResult.Continue("Номер класса должен быть от 1 до 11. Попробуйте снова:")
                    }
                    data["gradeNumber"] = gradeNumber.toString()
                    userSteps[userId] = 5
                    return CommandResult.Continue(
                        "Спасибо! Введите полное название класса (например, 5 ЕН, 3 гамма, 10А):"
                    )
                } catch (e: NumberFormatException) {
                    return CommandResult.Continue("Пожалуйста, введите номер класса цифрой от 1 до 11:")
                }
            }
            5 -> {
                data["gradeName"] = text.trim()
                userSteps[userId] = 6
                return CommandResult.Continue(
                    "Спасибо! Уточните уровень владения плавательными навыками (напишите цифру): \n" +
                            "1. Уверенно плавает\n" +
                            "2. Держится на воде\n" +
                            "3. Не умеет плавать"
                )
            }
            6 -> {
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

                val lastName = data["lastName"].orEmpty()
                val firstName = data["firstName"].orEmpty()
                val middleName = data["middleName"].orEmpty()
                val birthDate = data["birthDate"].orEmpty()
                val gradeNumber = data["gradeNumber"].orEmpty()
                val gradeName = data["gradeName"].orEmpty()
                val age = data["age"].orEmpty()

                // Формируем строку класса для отображения
                val classDisplay = if (gradeNumber == "0" || gradeNumber.isEmpty()) {
                    "—"
                } else {
                    val gradeNameDisplay = if (gradeName.isNotEmpty()) " ($gradeName)" else ""
                    "$gradeNumber$gradeNameDisplay"
                }

                userSteps[userId] = 7
                return CommandResult.Continue(
                    "Проверьте введенные данные:\n\n" +
                            "ФИО: $lastName $firstName $middleName\n" +
                            "Дата рождения: ${formatDate(birthDate)}\n" +
                            "Возраст: $age лет\n" +
                            "Класс: $classDisplay\n" +
                            "Навык: $skill\n\n" +
                            "Всё верно?\n" +
                            "Напишите 'да' для сохранения или 'нет' для отмены."
                )
            }
            7 -> {
                if (cmd != "да") {
                    userSteps.remove(userId)
                    userData.remove(userId)
                    return CommandResult.Cancel("Регистрация ребенка отменена.")
                }

                try {
                    val gradeNum = data["gradeNumber"]?.toIntOrNull() ?: 0
                    // Если gradeNum == 0, передаем 0, а в DatabaseService уже обработается как NULL

                    dbService.addChild(
                        userId,
                        data["firstName"].orEmpty(),
                        data["lastName"].orEmpty(),
                        data["middleName"].orEmpty(),
                        data["birthDate"].orEmpty(),
                        gradeNum,
                        data["gradeName"].orEmpty(),
                        data["skill"].orEmpty()
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

    private fun calculateAge(birthDateStr: String): Int {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val birthDate = LocalDate.parse(birthDateStr, formatter)
            val today = LocalDate.now()
            var age = today.year - birthDate.year
            if (today.monthValue < birthDate.monthValue ||
                (today.monthValue == birthDate.monthValue && today.dayOfMonth < birthDate.dayOfMonth)) {
                age--
            }
            return age
        } catch (_: Exception) {
            return 0
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
        return CommandResult.Cancel()
    }
}