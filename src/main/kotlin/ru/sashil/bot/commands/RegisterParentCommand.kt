package ru.sashil.bot.commands

import ru.sashil.bot.util.CommandUtils
import ru.sashil.common.service.DatabaseService
import java.util.regex.Pattern

class RegisterParentCommand(
    private val dbService: DatabaseService
) : BaseBotCommand() {
    override val displayName: String = "Зарегистрироваться как родитель"
    override val description: String = "Регистрация родителя в системе"

    private val emailPattern = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")

    override fun start(userId: Long): CommandResult {
        val isRegistered = try {
            dbService.isParentRegistered(userId)
        } catch (e: Exception) {
            false
        }
        if (isRegistered) {
            return CommandResult.Complete(
                "✅ Вы уже зарегистрированы в системе!\n" +
                        "Напишите 'меню' для просмотра всех команд."
            )
        }
        setStep(userId, 1)
        createData(userId)
        return CommandResult.Continue(
            "Добро пожаловать! Для регистрации в системе введите Вашу фамилию:"
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
                if (text.trim().length < 2) {
                    return CommandResult.Continue("Пожалуйста, введите корректную фамилию (минимум 2 символа):")
                }
                data["lastName"] = text.trim()
                setStep(userId, 2)
                return CommandResult.Continue("Введите Ваше имя:")
            }
            2 -> {
                if (text.trim().length < 2) {
                    return CommandResult.Continue("Пожалуйста, введите корректное имя (минимум 2 символа):")
                }
                data["firstName"] = text.trim()
                setStep(userId, 3)
                return CommandResult.Continue("Введите Ваше отчество (или '-'(тире/минус) для пропуска):")
            }
            3 -> {
                val middleName = if (CommandUtils.isSkipCommand(text)) "" else text.trim()
                data["middleName"] = middleName
                setStep(userId, 4)
                return CommandResult.Continue("Введите Ваш email (или '-'(тире/минус) для пропуска):")
            }
            4 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    if (!emailPattern.matcher(text).matches()) {
                        return CommandResult.Continue("❌ Неверный формат email. Попробуйте снова или '-'(тире/минус) для пропуска.")
                    }
                    data["email"] = text.trim()
                }
                setStep(userId, 5)
                return CommandResult.Continue("Введите Ваш номер телефона (формат: +7XXXXXXXXXX или 8XXXXXXXXXX):\nили '-'(тире/минус) для пропуска.")
            }
            5 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    val phone = text.trim()
                    val cleaned = phone.replace(Regex("\\D"), "")
                    if (cleaned.length !in 10..11) {
                        return CommandResult.Continue("❌ Неверный формат телефона. Используйте +7XXXXXXXXXX или 8XXXXXXXXXX.\nили '-'(тире/минус) для пропуска.")
                    }
                    data["phone"] = phone
                }

                val lastName = data["lastName"]?.toString().orEmpty()
                val firstName = data["firstName"]?.toString().orEmpty()
                val middleName = data["middleName"]?.toString().orEmpty().ifEmpty { "—" }
                val email = data["email"]?.toString().orEmpty().ifEmpty { "—" }
                val phone = data["phone"]?.toString().orEmpty().ifEmpty { "—" }

                setStep(userId, 6)
                return CommandResult.Continue(
                    "Проверьте введенные данные:\n" +
                            "Фамилия: $lastName\n" +
                            "Имя: $firstName\n" +
                            "Отчество: $middleName\n" +
                            "Email: $email\n" +
                            "Телефон: $phone\n" +
                            "Всё верно?\n" +
                            "Напишите 'да' для сохранения или 'нет' для отмены."
                )
            }
            6 -> {
                if (cmd != "да") {
                    removeSession(userId)
                    return CommandResult.Cancel("Регистрация отменена.")
                }
                try {
                    val firstName = data["firstName"]?.toString().orEmpty()
                    val lastName = data["lastName"]?.toString().orEmpty()
                    val middleName = data["middleName"]?.toString().orEmpty()
                    val email = data["email"]?.toString().orEmpty()
                    val phone = data["phone"]?.toString().orEmpty()

                    dbService.saveParentNullable(userId, firstName, lastName, middleName, email, phone)

                    removeSession(userId)
                    return CommandResult.Complete(
                        "✅ Регистрация завершена!\n" +
                                "Напишите 'меню' для просмотра всех команд."
                    )
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка сохранения: ${e.message}")
                }
            }
            else -> {
                return CommandResult.Cancel()
            }
        }
    }

    override fun cancel(userId: Long): CommandResult {
        removeSession(userId)
        return CommandResult.Cancel()
    }
}
