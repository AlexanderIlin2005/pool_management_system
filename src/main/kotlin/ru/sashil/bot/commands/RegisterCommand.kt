package ru.sashil.bot.commands

import ru.sashil.bot.util.CommandUtils
import ru.sashil.common.service.DatabaseService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RegisterCommand(
    private val dbService: DatabaseService
) : BaseBotCommand() {

    override val displayName: String = "Зарегистрировать ребенка в бассейн"
    override val description: String = "Регистрация нового ребенка для занятий"

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

        setStep(userId, 1)
        createData(userId)
        return CommandResult.Continue(
            "Вы хотите зарегистрировать ребенка для занятий в бассейне гимназии №642 «Земля и Вселенная» по адресу Морская набережная, 5?\n\n(Да/Нет)"
        )
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = getStep(userId)
        val data = getData(userId) ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (CommandUtils.isCancelCommand(text)) {
            removeSession(userId)
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
                setStep(userId, 2)
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
                setStep(userId, 3)
                return CommandResult.Continue("Спасибо! Введите дату рождения ребенка.\n\n(Например 31.10.2015)")
            }
            3 -> {
                val birthDateStr = text.trim()
                val birthDate = parseDate(birthDateStr)
                if (birthDate == null) {
                    return CommandResult.Continue("Неверный формат даты. Используйте ДД.ММ.ГГГГ")
                }
                data["birthDate"] = birthDate

                val age = calculateAge(birthDate)
                data["age"] = age.toString()

                if (age < 6) {
                    data["gradeNumber"] = "0"
                    data["gradeName"] = ""
                    setStep(userId, 6)
                    return CommandResult.Continue(
                        "✅ Возраст ребенка ($age лет) меньше 6 лет, поэтому мы пропускаем шаг с вводом класса.\n\n" +
                                "Уточните уровень владения плавательными навыками (напишите цифру): \n" +
                                "1. Уверенно плавает\n" +
                                "2. Держится на воде\n" +
                                "3. Не умеет плавать"
                    )
                } else if (age > 18) {
                    data["gradeNumber"] = "0"
                    data["gradeName"] = ""
                    setStep(userId, 6)
                    return CommandResult.Continue(
                        "✅ Мы распознали, что регистрируется взрослый человек для занятий в семейной группе или группе по аквааэробике.\n" +
                                "Ввод класса не требуется, так как он предназначен для школьников.\n\n" +
                                "Уточните Ваш уровень владения плавательными навыками (напишите цифру): \n" +
                                "1. Уверенно плавает\n" +
                                "2. Держится на воде\n" +
                                "3. Не умеет плавать"
                    )
                }

                setStep(userId, 4)
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
                    setStep(userId, 5)
                    return CommandResult.Continue(
                        "Спасибо! Введите полное название класса (например, 5 ЕН, 3 гамма, 10А):"
                    )
                } catch (e: NumberFormatException) {
                    return CommandResult.Continue("Пожалуйста, введите номер класса цифрой от 1 до 11:")
                }
            }
            5 -> {
                data["gradeName"] = text.trim()
                setStep(userId, 6)
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

                val lastName = data["lastName"]?.toString().orEmpty()
                val firstName = data["firstName"]?.toString().orEmpty()
                val middleName = data["middleName"]?.toString().orEmpty()
                val birthDate = data["birthDate"]?.toString().orEmpty()
                val gradeNumber = data["gradeNumber"]?.toString().orEmpty()
                val gradeName = data["gradeName"]?.toString().orEmpty()
                val age = data["age"]?.toString().orEmpty()

                val classDisplay = if (gradeNumber == "0" || gradeNumber.isEmpty()) {
                    "—"
                } else {
                    val gradeNameDisplay = if (gradeName.isNotEmpty()) " ($gradeName)" else ""
                    "$gradeNumber$gradeNameDisplay"
                }

                setStep(userId, 7)
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
                    removeSession(userId)
                    return CommandResult.Cancel("Регистрация ребенка отменена.")
                }

                try {
                    val gradeNum = data["gradeNumber"]?.toString()?.toIntOrNull() ?: 0

                    dbService.addChild(
                        userId,
                        data["firstName"]?.toString().orEmpty(),
                        data["lastName"]?.toString().orEmpty(),
                        data["middleName"]?.toString().orEmpty(),
                        data["birthDate"]?.toString().orEmpty(),
                        gradeNum,
                        data["gradeName"]?.toString().orEmpty(),
                        data["skill"]?.toString().orEmpty()
                    )
                    removeSession(userId)
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
        if (dateStr.isBlank()) return "—"
        try {
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                return "${parts[2]}.${parts[1]}.${parts[0]}"
            }
        } catch (_: Exception) {}
        return dateStr
    }

    override fun cancel(userId: Long): CommandResult {
        removeSession(userId)
        return CommandResult.Cancel()
    }
}
