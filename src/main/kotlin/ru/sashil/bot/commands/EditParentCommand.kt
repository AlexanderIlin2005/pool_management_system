package ru.sashil.bot.commands

import ru.sashil.bot.util.CommandUtils
import ru.sashil.common.service.DatabaseService
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class EditParentCommand(
    private val dbService: DatabaseService
) : BaseBotCommand() {
    override val displayName: String = "Редактировать свои данные"
    override val description: String = "Изменение личных данных родителя"

    private val emailPattern = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")

    override fun start(userId: Long): CommandResult {
        val isRegistered = try {
            dbService.isParentRegistered(userId)
        } catch (e: Exception) {
            false
        }
        if (!isRegistered) {
            return CommandResult.Complete("⚠️ Вы не зарегистрированы в системе.\nНапишите 'меню' для регистрации.")
        }

        val currentData = try {
            dbService.getParentData(userId)
        } catch (e: Exception) {
            null
        }

        if (currentData == null) {
            return CommandResult.Error("Не удалось получить Ваши данные")
        }

        setStep(userId, 1)
        val data = createData(userId)
        data["lastName"] = currentData["lastName"] ?: ""
        data["firstName"] = currentData["firstName"] ?: ""
        data["middleName"] = currentData["middleName"] ?: ""
        data["email"] = currentData["email"] ?: ""
        data["phone"] = currentData["phone"] ?: ""

        return CommandResult.Continue(
            "Редактирование профиля:\n\n" +
                    "Текущая фамилия: ${data["lastName"]}\n" +
                    "Введите новую фамилию (или '-' для пропуска):"
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
                if (!CommandUtils.isSkipCommand(text)) {
                    if (text.trim().length < 2) return CommandResult.Continue("Фамилия должна содержать минимум 2 символа.")
                    data["lastName"] = text.trim()
                }
                setStep(userId, 2)
                return CommandResult.Continue(
                    "Текущее имя: ${data["firstName"]}\n" +
                            "Введите новое имя (или '-' для пропуска):"
                )
            }
            2 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    if (text.trim().length < 2) return CommandResult.Continue("Имя должно содержать минимум 2 символа.")
                    data["firstName"] = text.trim()
                }
                setStep(userId, 3)
                return CommandResult.Continue(
                    "Текущее отчество: ${data["middleName"]?.toString() ?: "—"}\n" +
                            "Введите новое отчество (или '-' для пропуска):"
                )
            }
            3 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    data["middleName"] = text.trim()
                }
                setStep(userId, 4)
                return CommandResult.Continue(
                    "Текущий email: ${data["email"]?.toString() ?: "—"}\n" +
                            "Введите новый email (или '-' для пропуска):"
                )
            }
            4 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    if (!emailPattern.matcher(text).matches()) {
                        return CommandResult.Continue("❌ Неверный формат email. Попробуйте снова или '-' для пропуска.")
                    }
                    data["email"] = text.trim()
                }
                setStep(userId, 5)
                return CommandResult.Continue(
                    "Текущий телефон: ${data["phone"]?.toString() ?: "—"}\n" +
                            "Введите новый телефон (формат: +7XXXXXXXXXX или 8XXXXXXXXXX):\nили '-' для пропуска."
                )
            }
            5 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    val phone = text.trim()
                    val cleaned = phone.replace(Regex("\\D"), "")
                    if (cleaned.length !in 10..11) {
                        return CommandResult.Continue("❌ Неверный формат телефона. Используйте +7XXXXXXXXXX или 8XXXXXXXXXX.\nили '-' для пропуска.")
                    }
                    data["phone"] = phone
                }

                setStep(userId, 6)
                return CommandResult.Continue(
                    "Проверьте введенные данные:\n\n" +
                            "Фамилия: ${data["lastName"]}\n" +
                            "Имя: ${data["firstName"]}\n" +
                            "Отчество: ${data["middleName"]?.toString() ?: "—"}\n" +
                            "Email: ${data["email"]?.toString() ?: "—"}\n" +
                            "Телефон: ${data["phone"]?.toString() ?: "—"}\n\n" +
                            "Всё верно?\n" +
                            "Напишите 'да' для сохранения или 'нет' для отмены."
                )
            }
            6 -> {
                if (cmd != "да") {
                    removeSession(userId)
                    return CommandResult.Cancel("Редактирование отменено.")
                }
                try {
                    val firstName = data["firstName"]?.toString().orEmpty()
                    val lastName = data["lastName"]?.toString().orEmpty()
                    val middleName = data["middleName"]?.toString().orEmpty()
                    val email = data["email"]?.toString().orEmpty()
                    val phone = data["phone"]?.toString().orEmpty()

                    dbService.updateParentNullable(userId, firstName, lastName, middleName, email, phone)

                    removeSession(userId)
                    return CommandResult.Complete("✅ Ваши данные успешно обновлены!")
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
