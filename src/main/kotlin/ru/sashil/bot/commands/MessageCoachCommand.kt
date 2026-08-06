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
            val childId = (child["id"] as Number).toLong()
            val childName = "${child["lastName"]} ${child["firstName"]}"

            // Получаем уникальных тренеров ребенка
            val trainers = getUniqueTrainersForChild(childId)

            if (trainers.isEmpty()) {
                userSteps.remove(userId)
                return CommandResult.Complete(
                    "⚠️ Ваш ребенок ($childName) пока не зачислен ни в одну группу или у групп не назначены тренеры.\n\n" +
                            "Пожалуйста, свяжитесь с администратором через команду 6."
                )
            }

            val data = userData[userId]!!
            data["childId"] = childId.toString()
            data["childName"] = childName

            if (trainers.size == 1) {
                // Один тренер — сразу к сообщению
                data["trainerId"] = trainers[0].first.toString()
                data["trainerName"] = trainers[0].second
                userSteps[userId] = 3
                return CommandResult.Continue(
                    "Вы выбрали ребенка: $childName\n" +
                            "Тренер: ${trainers[0].second}\n\n" +
                            "Напишите Ваше сообщение для тренера.\n\n(Для отмены напишите 'отмена')"
                )
            } else {
                // Несколько тренеров — выбираем
                data["trainers"] = trainers.joinToString("|") { "${it.first}:${it.second}" }
                userSteps[userId] = 2
                val sb = StringBuilder("У Вашего ребенка несколько тренеров. Выберите, кому написать:\n\n")
                trainers.forEachIndexed { i, trainer ->
                    sb.append("${i + 1}. ${trainer.second}\n")
                }
                sb.append("\nВведите номер тренера:")
                return CommandResult.Continue(sb.toString())
            }
        } else {
            val sb = StringBuilder("Выберите ребенка:\n\n")
            children.forEachIndexed { i, child ->
                val name = "${child["lastName"]} ${child["firstName"]}"
                val middleName = child["middleName"] as? String
                if (!middleName.isNullOrEmpty()) {
                    sb.append("${i + 1}. $name $middleName\n")
                } else {
                    sb.append("${i + 1}. $name\n")
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
            // Шаг 1: Выбор ребенка (если их несколько)
            1 -> {
                val children = try {
                    dbService.getChildrenByParentVkId(userId)
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка получения данных: ${e.message}")
                }

                val num = text.trim().toIntOrNull()
                if (num == null || num < 1 || num > children.size) {
                    return CommandResult.Continue("Пожалуйста, введите номер ребенка от 1 до ${children.size}.")
                }

                val child = children[num - 1]
                val childId = (child["id"] as Number).toLong()
                val childName = "${child["lastName"]} ${child["firstName"]}"

                val trainers = getUniqueTrainersForChild(childId)

                if (trainers.isEmpty()) {
                    userSteps.remove(userId)
                    userData.remove(userId)
                    return CommandResult.Complete(
                        "⚠️ Ребенок $childName пока не зачислен ни в одну группу или у групп не назначены тренеры.\n\n" +
                                "Пожалуйста, выберите другого ребенка или свяжитесь с администратором через команду 6."
                    )
                }

                data["childId"] = childId.toString()
                data["childName"] = childName

                if (trainers.size == 1) {
                    data["trainerId"] = trainers[0].first.toString()
                    data["trainerName"] = trainers[0].second
                    userSteps[userId] = 3
                    return CommandResult.Continue(
                        "Вы выбрали ребенка: $childName\n" +
                                "Тренер: ${trainers[0].second}\n\n" +
                                "Напишите Ваше сообщение для тренера.\n\n(Для отмены напишите 'отмена')"
                    )
                } else {
                    data["trainers"] = trainers.joinToString("|") { "${it.first}:${it.second}" }
                    userSteps[userId] = 2
                    val sb = StringBuilder("У Вашего ребенка несколько тренеров. Выберите, кому написать:\n\n")
                    trainers.forEachIndexed { i, trainer ->
                        sb.append("${i + 1}. ${trainer.second}\n")
                    }
                    sb.append("\nВведите номер тренера:")
                    return CommandResult.Continue(sb.toString())
                }
            }

            // Шаг 2: Выбор тренера (если их несколько)
            2 -> {
                val trainersStr = data["trainers"] ?: return CommandResult.Error("Ошибка данных тренеров")
                val trainers = trainersStr.split("|").mapNotNull {
                    val parts = it.split(":", limit = 2)
                    if (parts.size == 2) Pair(parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1])
                    else null
                }

                val num = text.trim().toIntOrNull()
                if (num == null || num < 1 || num > trainers.size) {
                    return CommandResult.Continue("Пожалуйста, введите номер тренера от 1 до ${trainers.size}.")
                }

                val selectedTrainer = trainers[num - 1]
                data["trainerId"] = selectedTrainer.first.toString()
                data["trainerName"] = selectedTrainer.second
                userSteps[userId] = 3

                return CommandResult.Continue(
                    "Вы выбрали тренера: ${selectedTrainer.second}\n\n" +
                            "Напишите Ваше сообщение.\n\n(Для отмены напишите 'отмена')"
                )
            }

            // Шаг 3: Ввод и отправка сообщения
            3 -> {
                val childId = data["childId"]?.toLong() ?: return CommandResult.Error("Ребенок не выбран")
                val trainerId = data["trainerId"]?.toLong() ?: return CommandResult.Error("Тренер не выбран")
                val trainerName = data["trainerName"] ?: ""
                val childName = data["childName"] ?: "ребенок"

                val parentData = dbService.getParentData(userId)
                val parentName = if (parentData != null) {
                    "${parentData["lastName"]} ${parentData["firstName"]}"
                } else {
                    "Родитель (VK ID: $userId)"
                }

                // Находим группу, связывающую этого ребенка и этого тренера
                val groupId = getGroupIdForChildAndTrainer(childId, trainerId)

                try {
                    val sql = "INSERT INTO pool.messages " +
                            "(from_user_id, from_user_type, to_user_id, to_user_type, " +
                            "child_id, group_id, message_text, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)"

                    dbService.getConnection().use { conn ->
                        conn.prepareStatement(sql).use { stmt ->
                            val trainerInfo = if (trainerName.isNotEmpty()) " (тренер $trainerName)" else ""
                            val fullMessage = "Сообщение от $parentName (ребенок: $childName)$trainerInfo:\n\n$text"

                            stmt.setLong(1, userId)
                            stmt.setString(2, "PARENT")
                            stmt.setLong(3, trainerId)
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
                        "✅ Ваше сообщение отправлено тренеру ${trainerName}.\n\n" +
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

    /**
     * Возвращает список уникальных тренеров ребенка: List<Pair<trainerId, trainerName>>
     */
    private fun getUniqueTrainersForChild(childId: Long): List<Pair<Long, String>> {
        val trainers = mutableListOf<Pair<Long, String>>()
        try {
            val sql = "SELECT DISTINCT g.trainer_id, au.full_name " +
                    "FROM pool.group_children gc " +
                    "JOIN pool.groups g ON gc.group_id = g.id " +
                    "LEFT JOIN pool.admin_users au ON g.trainer_id = au.id " +
                    "WHERE gc.child_id = ? AND g.trainer_id IS NOT NULL"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, childId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val trainerId = rs.getLong("trainer_id")
                            val trainerName = rs.getString("full_name") ?: "Без имени"
                            trainers.add(Pair(trainerId, trainerName))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return trainers
    }

    /**
     * Находит ID группы, связывающей конкретного ребенка и конкретного тренера
     */
    private fun getGroupIdForChildAndTrainer(childId: Long, trainerId: Long): Long? {
        try {
            val sql = "SELECT gc.group_id FROM pool.group_children gc " +
                    "JOIN pool.groups g ON gc.group_id = g.id " +
                    "WHERE gc.child_id = ? AND g.trainer_id = ? LIMIT 1"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, childId)
                    stmt.setLong(2, trainerId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getLong("group_id")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun cancel(userId: Long): CommandResult {
        userSteps.remove(userId)
        userData.remove(userId)
        return CommandResult.Cancel()
    }
}