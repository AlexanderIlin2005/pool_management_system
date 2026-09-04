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
import ru.sashil.bot.util.CommandUtils
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
                    launch { startDailyNotificationScheduler(bot) }
                    launch { startInstantNotificationSender(bot) }
                    launch { startBroadcastListener(bot, dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD")) }
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

        private suspend fun CoroutineScope.startDailyNotificationScheduler(bot: VkClient) {
            while (isActive) {
                try { notificationService.checkDailyNotifications() }
                catch (e: Exception) { LOGGER.severe("Ошибка в планировщике ежедневных уведомлений: ${e.message}") }
                delay(60 * 60 * 1000)
            }
        }

        private suspend fun CoroutineScope.startInstantNotificationSender(bot: VkClient) {
            while (isActive) {
                try { notificationService.sendInstantNotifications() }
                catch (e: Exception) { LOGGER.severe("Ошибка в отправке мгновенных уведомлений: ${e.message}") }
                delay(60 * 1000)
            }
        }

        private suspend fun startBroadcastListener(bot: VkClient, dbUrl: String, dbUser: String, dbPass: String) {
            while (true) {
                try { checkAndSendBroadcasts(bot, dbUrl, dbUser, dbPass) }
                catch (e: Exception) { LOGGER.severe("Ошибка в слушателе рассылок: ${e.message}") }
                delay(30000)
            }
        }

        private suspend fun startMessageSender(bot: VkClient) {
            while (true) {
                try { notificationService.sendPendingMessagesToParents() }
                catch (e: Exception) { LOGGER.severe("Ошибка в отправке сообщений родителям: ${e.message}") }
                delay(10000)
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
                rs.close(); stmt.close()

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
                                bot.messages.send(vkId) { message = text; randomId = Random().nextInt(Int.MAX_VALUE) }
                                sentCount++
                                delay(100)
                            } catch (e: Exception) { LOGGER.warning("Ошибка отправки $vkId: ${e.message}") }
                        }
                        val updateSql = "UPDATE pool.broadcast_messages SET status = 'SENT', sent_count = ? WHERE id = ?"
                        conn.prepareStatement(updateSql).use { us -> us.setInt(1, sentCount); us.setLong(2, taskId); us.executeUpdate() }
                        LOGGER.info("Рассылка #$taskId выполнена. Получателей: $sentCount")
                        WebSocketNotifier.sendWebSocketNotification("BROADCAST_COMPLETED")
                    } catch (e: Exception) {
                        LOGGER.severe("Ошибка рассылки #$taskId: ${e.message}")
                        conn.prepareStatement("UPDATE pool.broadcast_messages SET status = 'ERROR' WHERE id = ?").use { es -> es.setLong(1, taskId); es.executeUpdate() }
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
            conn.prepareStatement(sql).use { stmt ->
                if (type != "ALL") stmt.setLong(1, groupId!!)
                stmt.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong("vk_id")) }
            }
            return ids
        }

        private suspend fun processUpdate(bot: VkClient, update: GetUpdatesVkMethod.Result.Update) {
            val msgNew = update.asMessageNew ?: return
            val msg = msgNew.message
            val userId = msg.fromId
            val text = msg.text ?: ""
            LOGGER.info("Сообщение от $userId: '$text'")
            val rawJson = try { update.obj.toString() } catch (e: Exception) { null }

            try {
                val activeCommand = userCommands[userId]
                if (activeCommand != null) {
                    val result = activeCommand.processMessage(userId, text, rawJson)
                    handleCommandResult(bot, userId, result)
                } else {
                    // Передаем rawJson для корректного восстановления команд загрузки файлов
                    handleNewCommand(bot, userId, text, rawJson)
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Ошибка обработки сообщения от $userId: ${e.message}", e)
                sendText(bot, userId, "❌ Произошла внутренняя ошибка. Попробуйте позже.")
            }
        }

        private suspend fun restorePreviousCommand(bot: VkClient, userId: Long, text: String, rawJson: String?): Boolean {
            try {
                val sql = "SELECT command_name, step FROM pool.bot_sessions WHERE user_id = ?"
                dbService.getConnection().use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setLong(1, userId)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                val commandName = rs.getString("command_name")
                                val dbStep = rs.getInt("step")
                                LOGGER.info("🔄 Попытка восстановления сессии для $userId: command=$commandName, dbStep=$dbStep, inputText='$text'")

                                val commandType = BotCommandType.fromClassName(commandName)
                                if (commandType == null) {
                                    LOGGER.warning("❌ Неизвестный тип команды '$commandName' для $userId")
                                    return false
                                }

                                val command = CommandFactory.createCommand(commandType, dbService, minioService)
                                if (command !is BaseBotCommand) {
                                    LOGGER.warning("❌ Команда $commandName не является BaseBotCommand")
                                    return false
                                }

                                val restored = sessionManager.restoreSession(userId, command)
                                if (!restored) {
                                    LOGGER.warning("❌ restoreSession вернул false для $userId ($commandName)")
                                    return false
                                }

                                val actualStep = command.getStep(userId)
                                LOGGER.info("✅ Сессия восстановлена: step=$actualStep (ожидался $dbStep)")

                                userCommands[userId] = command

                                // === БЕЗОПАСНАЯ ОБРАБОТКА: ловим исключения внутри команды ===
                                val result = try {
                                    command.processMessage(userId, text, rawJson)
                                } catch (e: Exception) {
                                    LOGGER.log(Level.SEVERE, "❌ Ошибка в processMessage после восстановления для $userId: ${e.message}", e)
                                    return false // Возвращаем false, но НЕ очищаем сессию
                                }

                                LOGGER.info("📨 Результат processMessage для $userId (step=$actualStep): ${result::class.simpleName}")
                                handleCommandResult(bot, userId, result)
                                return true
                            } else {
                                LOGGER.warning("⚠️ hasSession=true, но запись в БД не найдена для $userId")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "❌ Критическая ошибка восстановления сессии для $userId: ${e.message}", e)
            }
            return false
        }

        private suspend fun handleNewCommand(bot: VkClient, userId: Long, text: String, rawJson: String? = null) {
            val normalized = text.trim().lowercase()

            // === АВАРИЙНЫЙ ВЫХОД: всегда работает первым, даже при подвешенной сессии ===
            if (normalized == "меню" || normalized == "команды" || normalized == "все команды" || normalized == "помощь") {
                sessionManager.clearSession(userId)
                sendText(bot, userId, getStartMessage())
                return
            }

            // Отмена тоже должна работать глобально как аварийный выход
            if (CommandUtils.isCancelCommand(text)) {
                if (sessionManager.hasSession(userId)) {
                    sessionManager.clearSession(userId)
                    sendText(bot, userId, "Действие отменено.")
                    sendText(bot, userId, "Напишите 'меню' для просмотра доступных действий.")
                    return
                }
            }

            // === ВОССТАНОВЛЕНИЕ СЕССИИ ДО ОБРАБОТКИ НОВЫХ КОМАНД ===
            // Обеспечивает плавное молчаливое продолжение после перезагрузки
            if (sessionManager.hasSession(userId)) {
                val restored = restorePreviousCommand(bot, userId, text, rawJson)
                if (restored) {
                    // Сессия успешно восстановлена и сообщение обработано в старой команде
                    return
                }
                // Если restore вернул false, значит сессия в БД есть, но восстановить не удалось
                // (например, класс команды удален или JSON битый). Очищаем, чтобы не зациклить.
                LOGGER.warning("Не удалось восстановить существующую сессию для $userId, очищаю битую запись.")
                sessionManager.clearSession(userId)
            }

            val isRegistered = try { dbService.isParentRegistered(userId) } catch (e: Exception) { false }

            val commandNumber = text.trim().toIntOrNull()
            val commandType = commandNumber?.let { BotCommandType.fromNumber(it) }

            if (commandType != null) {
                if (!isRegistered && commandType != BotCommandType.REGISTER_PARENT && commandType != BotCommandType.HELP) {
                    sendText(bot, userId,
                        "⚠️ Для выполнения этой команды необходимо сначала зарегистрироваться.\n" +
                                "Напишите '1' для регистрации."
                    )
                    return
                }

                // Блок "У Вас есть незавершенный диалог" удален.
                // Теперь новая команда просто перезаписывает старую, если восстановление выше не сработало.
                // Сюда мы попадаем только если:
                // а) Сессии не было
                // б) Сессия была, но восстановление не удалось (и мы её очистили выше)

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
                    "Добро пожаловать! Для начала работы зарегистрируйтесь.\n" +
                            "Напишите '1' для регистрации."
                )
                return
            }

            sendText(bot, userId,
                "❌ Неизвестная команда.\n" +
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