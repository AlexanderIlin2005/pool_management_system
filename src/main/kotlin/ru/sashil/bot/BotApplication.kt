package ru.sashil.bot

import io.github.blackbaroness.vk.VkClient
import io.github.blackbaroness.vk.model.method.GetUpdatesVkMethod
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import ru.sashil.bot.commands.*
import ru.sashil.bot.util.SessionManager
import ru.sashil.bot.util.WebSocketNotifier
import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import ru.sashil.common.util.ConfigLoader
import ru.sashil.common.util.CommandUtils
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

class BotApplication {
    companion object {
        private val LOGGER = Logger.getLogger(BotApplication::class.java.name)
        private lateinit var dbService: DatabaseService
        private lateinit var minioService: MinIOService
        private lateinit var notificationService: NotificationService
        private lateinit var sessionManager: SessionManager

        private val userCommands = ConcurrentHashMap<Long, BotCommand>()
        private val commandData = ConcurrentHashMap<Long, MutableMap<String, Any>>()

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                ConfigLoader.load()

                val dbUrl = "jdbc:postgresql://${ConfigLoader.get("DB_HOST")}:${ConfigLoader.get("DB_PORT")}/${ConfigLoader.get("DB_NAME")}"
                dbService = DatabaseService(dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD"))
                minioService = MinIOService()
                sessionManager = SessionManager(dbService)

                val vkToken = ConfigLoader.get("VK_BOT_TOKEN")
                val groupId = 237058626L

                val bot = VkClient(
                    token = vkToken,
                    httpClient = HttpClient(CIO)
                )

                notificationService = NotificationService(dbService, bot)

                LOGGER.info("Бот запущен!")

                runBlocking {
                    LOGGER.info("Настройка LongPoll...")
                    bot.groups.setLongPollSettings(groupId) {
                        enabled = true
                        messageNew = true
                    }
                    LOGGER.info("LongPoll настроен.")

                    // ===== ПЛАНИРОВЩИКИ =====

                    // 1. Ежедневные уведомления (о завтрашних занятиях) — раз в час, только вечером
                    launch { startDailyNotificationScheduler(bot) }

                    // 2. Мгновенные уведомления (заявки, оплаты, изменения, сообщения) — каждую минуту
                    launch { startInstantNotificationSender(bot) }

                    // 3. Рассылки — каждые 30 секунд
                    launch { startBroadcastListener(bot, dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD")) }

                    // 4. Сообщения от админов/тренеров родителям — каждые 10 секунд
                    launch { startMessageSender(bot) }

                    LOGGER.info("Запуск LongPoll polling...")
                    bot.startLongPolling(groupId, null).collect { update ->
                        processUpdate(bot, update)
                    }
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Критическая ошибка: ${e.message}", e)
                e.printStackTrace()
            }
        }

        /**
         * Планировщик ежедневных уведомлений (о завтрашних занятиях)
         * Проверяет каждый час, но отправляет только вечером (18:00-21:00)
         */
        private suspend fun CoroutineScope.startDailyNotificationScheduler(bot: VkClient) {
            while (isActive) {
                try {
                    notificationService.checkDailyNotifications()
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в планировщике ежедневных уведомлений: ${e.message}")
                }
                delay(60 * 60 * 1000) // 1 час
            }
        }

        /**
         * Планировщик мгновенных уведомлений
         * Отправляет все остальные уведомления каждую минуту
         */
        private suspend fun CoroutineScope.startInstantNotificationSender(bot: VkClient) {
            while (isActive) {
                try {
                    notificationService.sendInstantNotifications()
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в отправке мгновенных уведомлений: ${e.message}")
                }
                delay(60 * 1000) // 1 минута
            }
        }

        private suspend fun startBroadcastListener(bot: VkClient, dbUrl: String, dbUser: String, dbPass: String) {
            while (true) {
                try {
                    checkAndSendBroadcasts(bot, dbUrl, dbUser, dbPass)
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в слушателе рассылок: ${e.message}")
                }
                delay(30000) // 30 секунд
            }
        }

        private suspend fun startMessageSender(bot: VkClient) {
            while (true) {
                try {
                    notificationService.sendPendingMessagesToParents()
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в отправке сообщений родителям: ${e.message}")
                }
                delay(10000) // 10 секунд
            }
        }

        private suspend fun checkAndSendBroadcasts(bot: VkClient, dbUrl: String, dbUser: String, dbPass: String) {
            DriverManager.getConnection(dbUrl, dbUser, dbPass).use { conn ->
                val selectSql = "SELECT id, target_type, target_group_id, message_text FROM pool.broadcast_messages WHERE status = 'PENDING'"
                val stmt = conn.prepareStatement(selectSql)
                val rs = stmt.executeQuery()
                val tasks = mutableListOf<Map<String, Any>>()
                while (rs.next()) {
                    tasks.add(mapOf(
                        "id" to rs.getLong("id"),
                        "type" to rs.getString("target_type"),
                        "groupId" to rs.getObject("target_group_id"),
                        "text" to rs.getString("message_text")
                    ))
                }
                rs.close()
                stmt.close()

                for (task in tasks) {
                    val taskId = task["id"] as Long
                    val type = task["type"] as String
                    val groupId = task["groupId"] as? Long
                    val text = task["text"] as String
                    var sentCount = 0
                    try {
                        val recipients = getRecipients(conn, type, groupId)
                        for (vkId in recipients) {
                            try {
                                bot.messages.send(vkId) {
                                    message = text
                                    randomId = Random().nextInt(Int.MAX_VALUE)
                                }
                                sentCount++
                                delay(100)
                            } catch (e: Exception) {
                                LOGGER.warning("Ошибка отправки $vkId: ${e.message}")
                            }
                        }
                        val updateSql = "UPDATE pool.broadcast_messages SET status = 'SENT', sent_count = ? WHERE id = ?"
                        val updateStmt = conn.prepareStatement(updateSql)
                        updateStmt.setInt(1, sentCount)
                        updateStmt.setLong(2, taskId)
                        updateStmt.executeUpdate()
                        updateStmt.close()
                        LOGGER.info("Рассылка #$taskId выполнена. Получателей: $sentCount")
                        WebSocketNotifier.sendWebSocketNotification("BROADCAST_COMPLETED")
                    } catch (e: Exception) {
                        LOGGER.severe("Ошибка рассылки #$taskId: ${e.message}")
                        val errorSql = "UPDATE pool.broadcast_messages SET status = 'ERROR' WHERE id = ?"
                        val errorStmt = conn.prepareStatement(errorSql)
                        errorStmt.setLong(1, taskId)
                        errorStmt.executeUpdate()
                        errorStmt.close()
                    }
                }
            }
        }

        private fun getRecipients(conn: java.sql.Connection, type: String, groupId: Long?): List<Long> {
            val ids = mutableListOf<Long>()
            val sql = if (type == "ALL") {
                "SELECT DISTINCT p.vk_id FROM pool.parents p WHERE p.vk_id IS NOT NULL"
            } else {
                "SELECT DISTINCT p.vk_id FROM pool.parents p JOIN pool.children c ON p.id = c.parent_id JOIN pool.group_children gc ON c.id = gc.child_id WHERE gc.group_id = ? AND p.vk_id IS NOT NULL"
            }
            val stmt = conn.prepareStatement(sql)
            if (type != "ALL") {
                stmt.setLong(1, groupId!!)
            }
            val rs = stmt.executeQuery()
            while (rs.next()) {
                ids.add(rs.getLong("vk_id"))
            }
            rs.close()
            stmt.close()
            return ids
        }

        private suspend fun processUpdate(bot: VkClient, update: GetUpdatesVkMethod.Result.Update) {
            val msgNew = update.asMessageNew ?: return
            val msg = msgNew.message
            val userId = msg.fromId
            val text = msg.text ?: ""

            LOGGER.info("Сообщение от $userId: '$text'")

            val rawJson = try {
                update.obj.toString()
            } catch (e: Exception) {
                null
            }

            try {
                val activeCommand = userCommands[userId]

                if (activeCommand != null) {
                    val result = activeCommand.processMessage(userId, text, rawJson)
                    handleCommandResult(bot, userId, result)
                } else {
                    // Проверяем наличие сессии в БД
                    if (sessionManager.hasSession(userId)) {
                        // Пытаемся восстановить предыдущую команду
                        val restored = restorePreviousCommand(bot, userId, text, rawJson)
                        if (restored) return
                    }

                    handleNewCommand(bot, userId, text)
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Ошибка обработки сообщения от $userId: ${e.message}", e)
                sendText(bot, userId, "❌ Произошла внутренняя ошибка. Попробуйте позже.")
            }
        }

        private suspend fun restorePreviousCommand(bot: VkClient, userId: Long, text: String, rawJson: String?): Boolean {
            try {
                val sql = "SELECT command_name FROM pool.bot_sessions WHERE user_id = ?"
                dbService.getConnection().use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setLong(1, userId)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                val commandName = rs.getString("command_name")
                                val commandType = BotCommandType.fromClassName(commandName)
                                if (commandType != null) {
                                    val command = CommandFactory.createCommand(commandType, dbService, minioService)
                                    if (command is BaseBotCommand) {
                                        if (sessionManager.restoreSession(userId, command)) {
                                            userCommands[userId] = command
                                            val result = command.processMessage(userId, text, rawJson)
                                            handleCommandResult(bot, userId, result)
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LOGGER.warning("Не удалось восстановить сессию: ${e.message}")
            }
            return false
        }

        private suspend fun handleNewCommand(bot: VkClient, userId: Long, text: String) {
            val isRegistered = try {
                dbService.isParentRegistered(userId)
            } catch (e: Exception) {
                false
            }

            val normalized = text.trim().lowercase()

            if (normalized == "меню" || normalized == "команды" || normalized == "все команды" || normalized == "помощь") {
                sessionManager.clearSession(userId)
                sendText(bot, userId, getStartMessage())
                return
            }

            val commandNumber = text.trim().toIntOrNull()
            val commandType = commandNumber?.let { BotCommandType.fromNumber(it) }

            if (commandType != null) {
                if (!isRegistered && commandType != BotCommandType.REGISTER_PARENT && commandType != BotCommandType.HELP) {
                    sendText(bot, userId,
                        "⚠️ Для выполнения этой команды необходимо сначала зарегистрироваться.\n\n" +
                                "Напишите '1' для регистрации."
                    )
                    return
                }

                if (isRegistered && sessionManager.hasSession(userId)) {
                    sendText(bot, userId,
                        "⚠️ У Вас есть незавершенный диалог. Чтобы продолжить, отправьте ответ на предыдущее сообщение.\n\n" +
                                "Если хотите начать заново, напишите 'отмена' или 'меню'."
                    )
                    return
                }

                val command = CommandFactory.createCommand(commandType, dbService, minioService)
                userCommands[userId] = command
                val data = mutableMapOf<String, Any>()
                commandData[userId] = data

                val result = command.start(userId)
                handleCommandResult(bot, userId, result)
                return
            }

            if (!isRegistered) {
                sendText(bot, userId,
                    "Добро пожаловать! Для начала работы зарегистрируйтесь.\n\n" +
                            "Напишите '1' для регистрации."
                )
                return
            }

            sendText(bot, userId,
                "❌ Неизвестная команда.\n\n" +
                        "Напишите 'меню' для просмотра доступных действий."
            )
        }

        private fun getStartMessage(): String {
            return """
        |Здравствуйте! Вас приветствует чат-бот бассейна гимназии №642 «Земля и Вселенная». С помощью бота Вы можете:
        |
        |${BotCommandType.getCommandsList()}
        |
        |Если Ваш ребенок посещает занятия в бассейне, Вы будете получать уведомления-напоминания о занятии, уведомление о необходимости оплатить абонемент, уведомления об изменении графика работы бассейна.
        |
        |Выберите нужное действие. Напишите соответствующую цифру.
    """.trimMargin()
        }

        private suspend fun handleCommandResult(bot: VkClient, userId: Long, result: CommandResult) {
            when (result) {
                is CommandResult.Complete -> {
                    userCommands.remove(userId)
                    commandData.remove(userId)
                    sessionManager.clearSession(userId)
                    sendText(bot, userId, result.message)
                }
                is CommandResult.Continue -> {
                    val command = userCommands[userId]
                    if (command is BaseBotCommand) {
                        sessionManager.saveSession(userId, command)
                    }
                    sendText(bot, userId, result.message)
                }
                is CommandResult.Cancel -> {
                    userCommands.remove(userId)
                    commandData.remove(userId)
                    sessionManager.clearSession(userId)
                    sendText(bot, userId, result.message)
                    sendText(bot, userId, "Напишите 'меню' для просмотра доступных действий.")
                }
                is CommandResult.Error -> {
                    sendText(bot, userId, "❌ " + result.message)
                }
            }
        }

        private suspend fun sendText(bot: VkClient, userId: Long, text: String) {
            try {
                bot.messages.send(userId) {
                    message = text
                    randomId = Random().nextInt(Int.MAX_VALUE)
                }
            } catch (e: Exception) {
                LOGGER.severe("Не удалось отправить сообщение пользователю $userId: ${e.message}")
            }
        }
    }
}