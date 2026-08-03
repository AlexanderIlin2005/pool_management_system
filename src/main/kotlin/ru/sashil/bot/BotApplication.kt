package ru.sashil.bot

import io.github.blackbaroness.vk.VkClient
import io.github.blackbaroness.vk.model.method.GetUpdatesVkMethod
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import ru.sashil.bot.commands.*
import ru.sashil.bot.util.WebSocketNotifier
import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import ru.sashil.common.util.ConfigLoader
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

        // Хранилище активных команд для пользователей
        private val userCommands = ConcurrentHashMap<Long, BotCommand>()
        private val commandData = ConcurrentHashMap<Long, MutableMap<String, Any>>()

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                ConfigLoader.load()

                val dbUrl = "jdbc:postgresql://${ConfigLoader.get("DB_HOST")}:${ConfigLoader.get("DB_PORT")}/${ConfigLoader.get("DB_NAME")}"
                dbService = DatabaseService(dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD"))
                minioService = MinIOService()

                val vkToken = ConfigLoader.get("VK_BOT_TOKEN")
                val groupId = 239874040L

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

                    launch { startNotificationScheduler(bot) }
                    launch { startBroadcastListener(bot, dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD")) }
                    launch { startPendingNotificationSender(bot) }
                    launch { sendMessageReplies(bot) }

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

        private suspend fun CoroutineScope.startNotificationScheduler(bot: VkClient) {
            while (isActive) {
                try {
                    notificationService.checkAndSendNotifications()
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в планировщике уведомлений: ${e.message}")
                }
                delay(60 * 60 * 1000)
            }
        }

        private suspend fun startBroadcastListener(bot: VkClient, dbUrl: String, dbUser: String, dbPass: String) {
            while (true) {
                try {
                    checkAndSendBroadcasts(bot, dbUrl, dbUser, dbPass)
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в слушателе рассылок: ${e.message}")
                }
                delay(30000)
            }
        }

        private suspend fun sendMessageReplies(bot: VkClient) {
            while (true) {
                try {
                    notificationService.sendPendingMessageReplies();
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в слушателе рассылок: ${e.message}")
                }
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

        private suspend fun CoroutineScope.startPendingNotificationSender(bot: VkClient) {
            while (isActive) {
                try {
                    // Уведомления о заявках
                    val joinNotifications = dbService.getPendingJoinRequestNotifications()
                    for (notif in joinNotifications) {
                        val vkId = notif["parent_vk_id"] as Long
                        val message = notif["message_text"] as String
                        val notifId = notif["id"] as Long
                        try {
                            sendText(bot, vkId, message)
                            dbService.markJoinRequestNotificationSent(notifId)
                        } catch (e: Exception) {
                            LOGGER.severe("Ошибка отправки уведомления о заявке: ${e.message}")
                        }
                    }

                    // Уведомления об изменении данных ребенка
                    val childUpdateNotifications = dbService.getPendingChildUpdateNotifications()
                    for (notif in childUpdateNotifications) {
                        val vkId = notif["parent_vk_id"] as Long
                        val message = notif["message_text"] as String
                        val notifId = notif["id"] as Long
                        try {
                            sendText(bot, vkId, message)
                            dbService.markChildUpdateNotificationSent(notifId)
                        } catch (e: Exception) {
                            LOGGER.severe("Ошибка отправки уведомления об изменении данных: ${e.message}")
                        }
                    }

                    // Уведомления об оплате
                    val paymentNotifications = dbService.getPendingPaymentNotifications()
                    for (notif in paymentNotifications) {
                        val vkId = notif["parent_vk_id"] as Long
                        val message = notif["message_text"] as String
                        val notifId = notif["id"] as Long
                        try {
                            sendText(bot, vkId, message)
                            dbService.markPaymentNotificationSent(notifId)
                        } catch (e: Exception) {
                            LOGGER.severe("Ошибка отправки уведомления об оплате: ${e.message}")
                        }
                    }

                    // Уведомления об изменении навыка
                    val skillNotifications = dbService.getPendingSkillNotifications()
                    for (notif in skillNotifications) {
                        val vkId = notif["vk_id"] as Long
                        val childName = notif["child_name"] as String
                        val oldSkill = notif["old_skill"] as String
                        val newSkill = notif["new_skill"] as String
                        val notifId = notif["id"] as Long

                        val text = "Навык плавания ребенка $childName был изменен с '$oldSkill' на '$newSkill'."
                        try {
                            sendText(bot, vkId, text)
                            dbService.markSkillNotificationSent(notifId)
                        } catch (e: Exception) {
                            LOGGER.severe("Ошибка отправки уведомления об изменении навыка: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в планировщике уведомлений: ${e.message}")
                }
                delay(60 * 1000)
            }
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
                    handleNewCommand(bot, userId, text)
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Ошибка обработки сообщения от $userId: ${e.message}", e)
                sendText(bot, userId, "❌ Произошла внутренняя ошибка. Попробуйте позже.")
            }
        }

        private suspend fun handleNewCommand(bot: VkClient, userId: Long, text: String) {
            val isRegistered = try {
                dbService.isParentRegistered(userId)
            } catch (e: Exception) {
                false
            }

            // Проверяем, не является ли текст командой
            val commandNumber = text.trim().toIntOrNull()
            val commandType = commandNumber?.let { BotCommandType.fromNumber(it) }

            if (commandType != null) {
                // Запускаем команду
                val command = CommandFactory.createCommand(commandType, dbService, minioService)
                userCommands[userId] = command
                commandData[userId] = mutableMapOf()

                val result = command.start(userId)
                handleCommandResult(bot, userId, result)
                return
            }

            // Если пользователь не зарегистрирован и команда не "начать" - предлагаем регистрацию
            if (!isRegistered) {
                sendText(bot, userId, getStartMessage())
                return
            }

            // Обработка отмены
            val normalized = text.trim().lowercase()
            if (normalized == "отмена" || normalized == "нет" || normalized == "cancel") {
                sendText(bot, userId, getStartMessage())
                return
            }

            // Если текст не является командой - показываем главное меню
            sendText(bot, userId, getStartMessage())
        }

        private suspend fun handleCommandResult(bot: VkClient, userId: Long, result: CommandResult) {
            when (result) {
                is CommandResult.Complete -> {
                    userCommands.remove(userId)
                    commandData.remove(userId)
                    sendText(bot, userId, result.message)
                    // После завершения показываем главное меню
                    sendText(bot, userId, getStartMessage())
                }
                is CommandResult.Continue -> {
                    sendText(bot, userId, result.message)
                }
                is CommandResult.Cancel -> {
                    userCommands.remove(userId)
                    commandData.remove(userId)
                    sendText(bot, userId, result.message)
                    sendText(bot, userId, getStartMessage())
                }
                is CommandResult.Error -> {
                    sendText(bot, userId, "❌ " + result.message)
                }
            }
        }

        private fun getStartMessage(): String {
            return """
                Здравствуйте! Вас приветствует чат-бот бассейна гимназии №642 «Земля и Вселенная». С помощью бота Вы можете: 

                ${BotCommandType.getCommandsList()}

                Если Ваш ребенок посещает занятия в бассейне, Вы будете получать уведомления-напоминания о занятии, уведомление о необходимости оплатить абонемент, уведомления об изменении графика работы бассейна.

                Выберите нужное действие. Напишите соответствующую цифру.
            """.trimIndent()
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