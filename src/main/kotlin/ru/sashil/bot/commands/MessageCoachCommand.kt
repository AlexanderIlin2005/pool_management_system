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

        val children = try {
            dbService.getChildrenByParentVkId(userId)
        } catch (e: Exception) {
            return CommandResult.Error("Ошибка получения данных: ${e.message}")
        }

        if (children.isEmpty()) {
            userSteps.remove(userId)
            return CommandResult.Complete(
                "У Вас пока нет зарегистрированных детей. Сначала зарегистрируйте ребенка (команда 1)."
            )
        }

        if (children.size == 1) {
            val child = children[0]
            val data = userData[userId]!!
            data["childId"] = (child["id"] as Number).toString()
            data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
            userSteps[userId] = 2
            return CommandResult.Continue(
                "Напишите Ваше сообщение для тренера.\n\n(Для отмены напишите 'отмена')"
            )
        } else {
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
                userSteps[userId] = 2

                // Получаем имя тренера для отображения
                val groupId = getChildGroupId(data["childId"]!!.toLong())
                val trainerName = getTrainerName(groupId)

                val trainerInfo = if (trainerName != null && trainerName.isNotEmpty()) {
                    " ($trainerName)"
                } else {
                    ""
                }

                return CommandResult.Continue(
                    "Вы выбрали ребенка: ${data["childName"]}\n" +
                            "Тренер: $trainerInfo\n\n" +
                            "Напишите Ваше сообщение для тренера.\n\n(Для отмены напишите 'отмена')"
                )
            }
            2 -> {
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
                    val trainerId = getTrainerId(groupId)
                    val trainerName = getTrainerName(groupId)

                    val sql = "INSERT INTO pool.messages " +
                            "(from_user_id, from_user_type, to_user_id, to_user_type, " +
                            "child_id, group_id, message_text, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)"

                    dbService.getConnection().use { conn ->
                        conn.prepareStatement(sql).use { stmt ->
                            val trainerInfo = if (trainerName != null && trainerName.isNotEmpty()) {
                                " (тренер $trainerName)"
                            } else {
                                ""
                            }
                            val fullMessage = "Сообщение от $parentName (ребенок: $childName)$trainerInfo:\n\n$text"

                            stmt.setLong(1, userId)
                            stmt.setString(2, "PARENT")
                            stmt.setLong(3, trainerId ?: -1)
                            stmt.setString(4, "COACH")
                            stmt.setLong(5, childId)
                            if (groupId != null) {
                                stmt.setLong(6, groupId)
                            } else {
                                stmt.setNull(6, java.sql.Types.BIGINT)
                            }
                            stmt.setString(7, fullMessage)
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
                userSteps.remove(userId)
                userData.remove(userId)
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

    private fun getTrainerId(groupId: Long?): Long? {
        if (groupId == null) return null
        try {
            val sql = "SELECT trainer_id FROM pool.groups WHERE id = ?"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, groupId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getLong("trainer_id")
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getTrainerName(groupId: Long?): String? {
        if (groupId == null) return null
        try {
            val sql = "SELECT au.full_name FROM pool.groups g " +
                    "LEFT JOIN pool.admin_users au ON g.trainer_id = au.id " +
                    "WHERE g.id = ?"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, groupId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getString("full_name")
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    override fun cancel(userId: Long): CommandResult {
        userSteps.remove(userId)
        userData.remove(userId)
        return CommandResult.Cancel()
    }
}