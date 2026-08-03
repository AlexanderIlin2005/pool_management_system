package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.CommandUtils
import java.util.concurrent.ConcurrentHashMap

class MessageAdminCommand(
    private val dbService: DatabaseService
) : BotCommand {

    override val displayName: String = "Написать сообщение администратору"
    override val description: String = "Связь с администрацией"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, String>>()

    override fun start(userId: Long): CommandResult {
        userSteps[userId] = 1
        userData[userId] = mutableMapOf()
        return CommandResult.Continue(
            "Напишите Ваше сообщение для администратора.\n\n(Для отмены напишите 'отмена')"
        )
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = userSteps[userId] ?: return CommandResult.Error("Сессия не найдена")
        val data = userData[userId] ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (cmd == "отмена" || cmd == "нет") {
            userSteps.remove(userId)
            userData.remove(userId)
            return CommandResult.Cancel()
        }

        when (step) {
            1 -> {
                try {
                    val parentData = dbService.getParentData(userId)
                    val parentName = if (parentData != null) {
                        "${parentData["lastName"]} ${parentData["firstName"]}"
                    } else {
                        "Родитель (VK ID: $userId)"
                    }

                    // Сохраняем сообщение в таблицу messages (для всех админов)
                    val sql = "INSERT INTO pool.messages " +
                            "(from_user_id, from_user_type, to_user_type, " +
                            "message_text, status, created_at) " +
                            "VALUES (?, 'PARENT', 'ADMIN', ?, 'PENDING', CURRENT_TIMESTAMP)"

                    dbService.getConnection().use { conn ->
                        conn.prepareStatement(sql).use { stmt ->
                            val fullMessage = "Сообщение от $parentName:\n\n$text"
                            stmt.setLong(1, userId)
                            stmt.setString(2, fullMessage)
                            stmt.executeUpdate()
                        }
                    }

                    userSteps.remove(userId)
                    userData.remove(userId)

                    return CommandResult.Complete(
                        "✅ Ваше сообщение отправлено администратору.\n\n" +
                                "Администратор свяжется с Вами в ближайшее время."
                    )
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка отправки сообщения: ${e.message}")
                }
            }
            else -> {
                userSteps.remove(userId)
                userData.remove(userId)
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