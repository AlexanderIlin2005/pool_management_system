package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.CommandUtils
import java.util.concurrent.ConcurrentHashMap

class MessageCoachCommand(
    private val dbService: DatabaseService
) : BotCommand {

    override val displayName: String = "Написать сообщение тренеру"
    override val description: String = "Связь с тренером"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, String>>()

    override fun start(userId: Long): CommandResult {
        userSteps[userId] = 1
        userData[userId] = mutableMapOf()
        return CommandResult.Continue(
            "Выберите ребенка, тренеру которого Вы хотите написать:\n\n" +
            "(Сначала зарегистрируйте ребенка через команду 1, если еще не сделали этого)"
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
                val children = try {
                    dbService.getChildrenByParentVkId(userId)
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка получения данных: ${e.message}")
                }

                if (children.isEmpty()) {
                    return CommandResult.Continue(
                        "У Вас пока нет зарегистрированных детей. Сначала зарегистрируйте ребенка (команда 1)."
                    )
                }

                if (children.size == 1) {
                    val child = children[0]
                    data["childId"] = (child["id"] as Number).toString()
                    data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
                    userSteps[userId] = 2
                    return CommandResult.Continue(
                        "Напишите Ваше сообщение для тренера.\n\n(Для отмены напишите 'отмена')"
                    )
                } else {
                    userSteps[userId] = 2
                    val sb = StringBuilder("Выберите ребенка:\n\n")
                    children.forEachIndexed { i, child ->
                        val name = (child["lastName"] as String) + " " + (child["firstName"] as String)
                        if (child["middleName"] != null && (child["middleName"] as String).isNotEmpty()) {
                            sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]} ${child["middleName"]}\n")
                        } else {
                            sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]}\n")
                        }
                    }
                    sb.append("\nВведите номер ребенка:")
                    return CommandResult.Continue(sb.toString())
                }
            }
            2 -> {
                val childIdStr = data["childId"]

                if (childIdStr == null) {
                    val children = try {
                        dbService.getChildrenByParentVkId(userId)
                    } catch (e: Exception) {
                        return CommandResult.Error("Ошибка получения данных: ${e.message}")
                    }

                    val num = text.trim().toIntOrNull()
                    if (num == null || num < 1 || num > children.size) {
                        return CommandResult.Continue(
                            "Пожалуйста, введите номер ребенка от 1 до ${children.size}."
                        )
                    }
                    val child = children[num - 1]
                    data["childId"] = (child["id"] as Number).toString()
                    data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
                    userSteps[userId] = 3
                    return CommandResult.Continue(
                        "Напишите Ваше сообщение для тренера.\n\n(Для отмены напишите 'отмена')"
                    )
                } else {
                    val childId = childIdStr.toLong()
                    val parentData = dbService.getParentData(userId)
                    val parentName = if (parentData != null) {
                        "${parentData["lastName"]} ${parentData["firstName"]}"
                    } else {
                        "Родитель (VK ID: $userId)"
                    }

                    val childName = data["childName"] ?: "ребенок"

                    try {
                        val groupId = getChildGroupId(childId)
                        val sql = """
                            INSERT INTO pool.broadcast_messages
                            (sender_id, target_type, target_group_id, message_text, created_at, status)
                            VALUES (NULL, 'GROUP', ?, ?, CURRENT_TIMESTAMP, 'PENDING')
                        """.trimIndent()

                        dbService.getConnection().use { conn ->
                            conn.prepareStatement(sql).use { stmt ->
                                val fullMessage = "Сообщение от $parentName (ребенок: $childName):\n\n$text"
                                stmt.setLong(1, groupId ?: -1)
                                stmt.setString(2, fullMessage)
                                stmt.executeUpdate()
                            }
                        }

                        userSteps.remove(userId)
                        userData.remove(userId)

                        return CommandResult.Complete(
                            "✅ Ваше сообщение отправлено тренеру.\n\n" +
                            "Тренер свяжется с Вами в ближайшее время."
                        )
                    } catch (e: Exception) {
                        return CommandResult.Error("Ошибка отправки сообщения: ${e.message}")
                    }
                }
            }
            3 -> {
                val childId = data["childId"]!!.toLong()
                val parentData = dbService.getParentData(userId)
                val parentName = if (parentData != null) {
                    "${parentData["lastName"]} ${parentData["firstName"]}"
                } else {
                    "Родитель (VK ID: $userId)"
                }

                val childName = data["childName"] ?: "ребенок"

                try {
                    val groupId = getChildGroupId(childId)
                    val sql = """
                        INSERT INTO pool.broadcast_messages
                        (sender_id, target_type, target_group_id, message_text, created_at, status)
                        VALUES (NULL, 'GROUP', ?, ?, CURRENT_TIMESTAMP, 'PENDING')
                    """.trimIndent()

                    dbService.getConnection().use { conn ->
                        conn.prepareStatement(sql).use { stmt ->
                            val fullMessage = "Сообщение от $parentName (ребенок: $childName):\n\n$text"
                            stmt.setLong(1, groupId ?: -1)
                            stmt.setString(2, fullMessage)
                            stmt.executeUpdate()
                        }
                    }

                    userSteps.remove(userId)
                    userData.remove(userId)

                    return CommandResult.Complete(
                        "✅ Ваше сообщение отправлено тренеру.\n\n" +
                        "Тренер свяжется с Вами в ближайшее время."
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

    private fun getChildGroupId(childId: Long): Long? {
        try {
            val sql = "SELECT group_id FROM pool.group_children WHERE child_id = ? LIMIT 1"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, childId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getLong("group_id")
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }
}
