package ru.sashil.bot.commands

import ru.sashil.bot.util.CommandUtils
import ru.sashil.common.service.DatabaseService
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class EditParentCommand(
    private val dbService: DatabaseService
) : BotCommand {
    override val displayName: String = "Редактировать свои данные"
    override val description: String = "Изменение личных данных родителя"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, String>>()
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

        userSteps[userId] = 1
        val data = mutableMapOf<String, String>()
        data["lastName"] = currentData["lastName"].orEmpty()
        data["firstName"] = currentData["firstName"].orEmpty()
        data["middleName"] = currentData["middleName"].orEmpty()
        data["email"] = currentData["email"].orEmpty()
        data["phone"] = currentData["phone"].orEmpty()
        userData[userId] = data

        return CommandResult.Continue(
            "Редактирование профиля:\n\n" +
                    "Текущая фамилия: ${data["lastName"]}\n" +
                    "Введите новую фамилию (или '-' для пропуска):"
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
                if (!CommandUtils.isSkipCommand(text)) {
                    if (text.trim().length < 2) return CommandResult.Continue("Фамилия должна содержать минимум 2 символа.")
                    data["lastName"] = text.trim()
                }
                userSteps[userId] = 2
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
                userSteps[userId] = 3
                return CommandResult.Continue(
                    "Текущее отчество: ${data["middleName"]?.ifEmpty { "—" } ?: "—"}\n" +
                            "Введите новое отчество (или '-' для пропуска):"
                )
            }
            3 -> {
                if (!CommandUtils.isSkipCommand(text)) {
                    data["middleName"] = text.trim()
                }
                userSteps[userId] = 4
                return CommandResult.Continue(
                    "Текущий email: ${data["email"]?.ifEmpty { "—" } ?: "—"}\n" +
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
                userSteps[userId] = 5
                return CommandResult.Continue(
                    "Текущий телефон: ${data["phone"]?.ifEmpty { "—" } ?: "—"}\n" +
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

                userSteps[userId] = 6
                return CommandResult.Continue(
                    "Проверьте введенные данные:\n\n" +
                            "Фамилия: ${data["lastName"]}\n" +
                            "Имя: ${data["firstName"]}\n" +
                            "Отчество: ${data["middleName"]?.ifEmpty { "—" } ?: "—"}\n" +
                            "Email: ${data["email"]?.ifEmpty { "—" } ?: "—"}\n" +
                            "Телефон: ${data["phone"]?.ifEmpty { "—" } ?: "—"}\n\n" +
                            "Всё верно?\n" +
                            "Напишите 'да' для сохранения или 'нет' для отмены."
                )
            }
            6 -> {
                if (cmd != "да") {
                    userSteps.remove(userId)
                    userData.remove(userId)
                    return CommandResult.Cancel("Редактирование отменено.")
                }
                try {
                    val firstName = data["firstName"].orEmpty()
                    val lastName = data["lastName"].orEmpty()
                    val middleName = data["middleName"].orEmpty()
                    val email = data["email"].orEmpty()
                    val phone = data["phone"].orEmpty()

                    dbService.updateParentNullable(userId, firstName, lastName, middleName, email, phone)

                    userSteps.remove(userId)
                    userData.remove(userId)
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
        userSteps.remove(userId)
        userData.remove(userId)
        return CommandResult.Cancel()
    }
}