package ru.sashil.bot

import io.github.blackbaroness.vk.VkClient
import io.github.blackbaroness.vk.model.method.GetUpdatesVkMethod
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import ru.sashil.bot.handlers.*
import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import ru.sashil.common.util.CommandUtils
import ru.sashil.common.util.ConfigLoader
import java.sql.DriverManager
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class BotApplication {
    companion object {
        private val LOGGER = Logger.getLogger(BotApplication::class.java.name)
        private lateinit var dbService: DatabaseService
        private lateinit var regHandler: RegistrationHandler
        private lateinit var editHandler: ProfileEditHandler
        private lateinit var childHandler: ChildRegistrationHandler
        private lateinit var childEditHandler: ChildEditHandler
        private lateinit var notificationService: NotificationService
        private lateinit var certificateHandler: CertificateHandler
        private lateinit var minioService: MinIOService

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                ConfigLoader.load()

                val dbUrl = "jdbc:postgresql://${ConfigLoader.get("DB_HOST")}:${ConfigLoader.get("DB_PORT")}/${ConfigLoader.get("DB_NAME")}"
                dbService = DatabaseService(dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD"))

                // Инициализация MinIO
                minioService = MinIOService()

                regHandler = RegistrationHandler()
                editHandler = ProfileEditHandler()
                childHandler = ChildRegistrationHandler()
                childEditHandler = ChildEditHandler()

                val vkToken = ConfigLoader.get("VK_BOT_TOKEN")
                val groupId = 239874040L

                val bot = VkClient(
                    token = vkToken,
                    httpClient = HttpClient(CIO)
                )

                // Инициализация сервисов
                notificationService = NotificationService(dbService, bot)
                // Инициализируем Java-хэндлер (VK клиент внутри него инициализируется лениво)
                certificateHandler = CertificateHandler(dbService, minioService)

                LOGGER.info("Бот запущен!")

                // --- ТЕСТОВАЯ ОТПРАВКА ---
                runBlocking {
                    try {
                        LOGGER.info("Отправка тестового сообщения на VK ID 986308...")
                        bot.messages.send(986308) {
                            message = "Тест запуска бота. Если ты это видишь, значит отправка работает."
                            randomId = Random().nextInt(Int.MAX_VALUE)
                        }
                        LOGGER.info("✅ Тестовое сообщение успешно отправлено!")
                    } catch (e: Exception) {
                        LOGGER.severe("❌ Ошибка отправки тестового сообщения: ${e.message}")
                        e.printStackTrace()
                    }
                }
                // -------------------------

                runBlocking {
                    // Настройка LongPoll settings
                    LOGGER.info("Настройка LongPoll...")
                    bot.groups.setLongPollSettings(groupId) {
                        enabled = true
                        messageNew = true
                        // Добавим другие типы событий для отладки, если нужно
                        // messageEvent = true
                    }
                    LOGGER.info("LongPoll настроен.")

                    launch { startNotificationScheduler(bot) }
                    launch { startBroadcastListener(bot, dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD")) }

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
            // Логируем тип обновления, чтобы видеть, приходят ли вообще события
            LOGGER.info("Получено обновление типа: ${update.type}")

            val msgNew = update.asMessageNew ?: run {
                // Если это не новое сообщение, можно залогировать тип для отладки
                // LOGGER.fine("Пропущено обновление типа: ${update.type}")
                return
            }

            val msg = msgNew.message
            val userId = msg.fromId
            val text = msg.text

            LOGGER.info("Новое сообщение от $userId: '$text'")

            // Получаем сырую JSON-строку объекта сообщения.
            val rawJson = try {
                update.obj.toString()
            } catch (e: Exception) {
                LOGGER.warning("Не удалось получить rawJson: ${e.message}")
                null
            }

            try {
                when {
                    regHandler.isRegistering(userId) -> handleRegistration(bot, userId, text)
                    editHandler.isEditing(userId) -> handleEditProfile(bot, userId, text)
                    childHandler.isAddingChild(userId) -> handleAddChild(bot, userId, text)
                    childEditHandler.isEditingChild(userId) -> handleEditChild(bot, userId, text)
                    certificateHandler.isUploading(userId) -> {
                        // Передаем управление Java-хэндлеру
                        val response = certificateHandler.processStep(userId, text, rawJson)
                        if (response != null) {
                            sendText(bot, userId, response)
                        }
                    }
                    else -> handleCommands(bot, userId, text)
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Ошибка обработки сообщения от $userId: ${e.message}", e)
                bot.messages.send(userId) {
                    message = "Произошла внутренняя ошибка."
                    randomId = Random().nextInt(Int.MAX_VALUE)
                }
            }
        }

        private suspend fun handleRegistration(bot: VkClient, userId: Long, text: String) {
            val result = regHandler.processStep(userId, text)
            if (result == "SAVE_PARENT") {
                val data = regHandler.getData(userId)
                dbService.saveParent(userId, data["firstName"], data["lastName"], data["middleName"], data["email"])
                sendText(bot, userId, "Регистрация завершена.")
                regHandler.clearData(userId)
            } else {
                sendText(bot, userId, result)
            }
        }

        private suspend fun handleEditProfile(bot: VkClient, userId: Long, text: String) {
            val result = editHandler.processStep(userId, text, dbService)
            sendText(bot, userId, result)
        }

        private suspend fun handleAddChild(bot: VkClient, userId: Long, text: String) {
            try {
                val result = childHandler.processStep(userId, text, dbService)
                sendText(bot, userId, result)
            } catch (e: Exception) {
                sendText(bot, userId, "Ошибка при добавлении: ${e.message}")
                childHandler.cancel(userId)
            }
        }

        private suspend fun handleEditChild(bot: VkClient, userId: Long, text: String) {
            try {
                val result = childEditHandler.processStep(userId, text, dbService)
                sendText(bot, userId, result)
            } catch (e: Exception) {
                sendText(bot, userId, "Ошибка при обновлении: ${e.message}")
                childEditHandler.cancel(userId)
            }
        }

        private suspend fun handleCommands(bot: VkClient, userId: Long, text: String) {
            val cmd = CommandUtils.normalize(text)
            when (cmd) {
                "начать", "start" -> {
                    if (dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Вы уже зарегистрированы.")
                    } else {
                        sendText(bot, userId, "Добро пожаловать! Введите вашу фамилию:")
                        regHandler.startRegistration(userId)
                    }
                }
                "редактировать", "профиль" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        val data = dbService.getParentData(userId)
                        if (data != null) {
                            editHandler.startEditing(userId, data)
                            sendText(bot, userId, "Режим редактирования. Текущая фамилия: ${data["lastName"]}. Введите новую:")
                        }
                    }
                }
                "справка" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        certificateHandler.startUpload(userId)
                    }
                }
                "добавитьребенка" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        childHandler.startAddingChild(userId)
                        sendText(bot, userId, "Введите фамилию ребенка:")
                    }
                }
                "дети" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        val children = dbService.getChildrenByParentVkId(userId)
                        if (children.isEmpty()) {
                            sendText(bot, userId, "У вас пока нет детей.")
                        } else {
                            val sb = StringBuilder("Ваши дети:\n")
                            children.forEachIndexed { i, c ->
                                sb.append("${i + 1}. ${c["lastName"]} ${c["firstName"]} (${c["age"]} лет, ${c["gradeNumber"]} кл.)\n")
                            }
                            sb.append("\nНапишите номер для редактирования.")
                            sendText(bot, userId, sb.toString())
                        }
                    }
                }
                "уведомления" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        val isEnabled = dbService.isRegularNotificationsEnabled(userId)
                        val status = if (isEnabled) "включены" else "выключены"
                        sendText(bot, userId, "Регулярные напоминания о занятиях сейчас $status.\nНапишите 'включить уведомления' или 'выключить уведомления', чтобы изменить.")
                    }
                }
                "включитьуведомления" -> {
                    dbService.toggleRegularNotifications(userId, true)
                    sendText(bot, userId, "Регулярные напоминания включены.")
                }
                "выключитьуведомления" -> {
                    dbService.toggleRegularNotifications(userId, false)
                    sendText(bot, userId, "Регулярные напоминания выключены.")
                }
                "договор", "согласие", "правила", "квитанция" -> {
                    sendText(bot, userId, "Отправка документов временно отключена.")
                }
                else -> {
                    if (text.matches("\\d+".toRegex())) {
                        val num = text.toInt()
                        val children = dbService.getChildrenByParentVkId(userId)
                        if (num > 0 && num <= children.size) {
                            val child = children[num - 1]
                            childEditHandler.startEditingChild(userId, (child["id"] as Long), child)
                            sendText(bot, userId, "Редактирование: ${child["lastName"]}. Введите новую фамилию:")
                        }
                    }
                }
            }
        }

        private suspend fun sendText(bot: VkClient, userId: Long, text: String) {
            LOGGER.info("Попытка отправки сообщения пользователю $userId: $text")
            try {
                bot.messages.send(userId) {
                    message = text
                    randomId = Random().nextInt(Int.MAX_VALUE)
                }
                LOGGER.info("Сообщение успешно отправлено пользователю $userId")
            } catch (e: Exception) {
                LOGGER.severe("НЕ УДАЛОСЬ отправить сообщение пользователю $userId: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}