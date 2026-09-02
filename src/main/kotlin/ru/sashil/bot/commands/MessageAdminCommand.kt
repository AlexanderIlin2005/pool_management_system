package ru.sashil.bot.commands

import ru.sashil.bot.util.CommandUtils
import ru.sashil.common.service.DatabaseService

class MessageAdminCommand(
    private val dbService: DatabaseService
) : BaseBotCommand() {

    override val displayName: String = "Написать сообщение администратору"
    override val description: String = "Связь с администрацией"

    override fun start(userId: Long): CommandResult {
        setStep(userId, 1)
        createData(userId)
        return CommandResult.Continue(
            "Напишите Ваше сообщение для администратора.\n\n(Для отмены напишите 'отмена')"
        )
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = getStep(userId)
        val data = getData(userId) ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (cmd == "отмена" || cmd == "нет") {
            removeSession(userId)
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

                    removeSession(userId)

                    return CommandResult.Complete(
                        "✅ Ваше сообщение отправлено администратору.\n\n" +
                                "Администратор свяжется с Вами в ближайшее время."
                    )
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка отправки сообщения: ${e.message}")
                }
            }
            else -> {
                removeSession(userId)
                return CommandResult.Cancel()
            }
        }
    }

    override fun cancel(userId: Long): CommandResult {
        removeSession(userId)
        return CommandResult.Cancel()
    }
}
