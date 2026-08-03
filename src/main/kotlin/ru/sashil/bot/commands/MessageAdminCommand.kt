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

                    val sql = """
                        INSERT INTO pool.broadcast_messages
                        (sender_id, target_type, message_text, created_at, status)
                        VALUES (NULL, 'ALL', ?, CURRENT_TIMESTAMP, 'PENDING')
                    """.trimIndent()

                    dbService.getConnection().use { conn ->
                        conn.prepareStatement(sql).use { stmt ->
                            val fullMessage = "Сообщение от $parentName:\n\n$text"
                            stmt.setString(1, fullMessage)
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
                return CommandResult.Cancel()
            }
        }
    }
}
