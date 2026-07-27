package ru.sashil.bot

import io.github.blackbaroness.vk.VkClient
import io.github.blackbaroness.vk.model.method.GetUpdatesVkMethod
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import ru.sashil.bot.handlers.*
import ru.sashil.bot.util.WebSocketNotifier
import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import ru.sashil.common.util.CommandUtils
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
        private lateinit var regHandler: RegistrationHandler
        private lateinit var editHandler: ProfileEditHandler
        private lateinit var childHandler: ChildRegistrationHandler
        private lateinit var childEditHandler: ChildEditHandler
        private lateinit var notificationService: NotificationService
        private lateinit var certificateHandler: CertificateHandler
        private lateinit var paymentReceiptHandler: PaymentReceiptHandler
        private lateinit var minioService: MinIOService

        // Хранилище для потока записи в группу
        private val joinRequestSteps = ConcurrentHashMap<Long, Int>() // 1: выбор ребенка, 2: выбор группы
        private val joinRequestTempData = ConcurrentHashMap<Long, MutableMap<String, Any>>() // временные данные


        @JvmStatic
        fun main(args: Array<String>) {
            try {
                ConfigLoader.load()

                val dbUrl = "jdbc:postgresql://${ConfigLoader.get("DB_HOST")}:${ConfigLoader.get("DB_PORT")}/${ConfigLoader.get("DB_NAME")}"
                dbService = DatabaseService(dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD"))

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

                notificationService = NotificationService(dbService, bot)
                certificateHandler = CertificateHandler(dbService, minioService)
                paymentReceiptHandler = PaymentReceiptHandler(dbService, minioService)

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
                    launch { startJoinRequestNotificationSender(bot) } // Новый планировщик

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
                delay(60 * 60 * 1000) // Раз в час для обычных уведомлений
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

        // Планировщик отправки уведомлений о заявках (раз в минуту)
        private suspend fun CoroutineScope.startJoinRequestNotificationSender(bot: VkClient) {
            while (isActive) {
                try {
                    val notifications = dbService.getPendingJoinRequestNotifications()
                    for (notif in notifications) {
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
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в планировщике уведомлений о заявках: ${e.message}")
                }
                delay(60 * 1000) // Раз в минуту
            }
        }

        private suspend fun processUpdate(bot: VkClient, update: GetUpdatesVkMethod.Result.Update) {
            LOGGER.info("Получено обновление типа: ${update.type}")

            val msgNew = update.asMessageNew ?: run {
                return
            }

            val msg = msgNew.message
            val userId = msg.fromId
            val text = msg.text

            LOGGER.info("Новое сообщение от $userId: '$text'")

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
                        val response = certificateHandler.processStep(userId, text, rawJson)
                        if (response != null) sendText(bot, userId, response)
                    }
                    paymentReceiptHandler.isUploading(userId) -> {
                        val response = paymentReceiptHandler.processStep(userId, text, rawJson)
                        if (response != null) sendText(bot, userId, response)
                    }
                    joinRequestSteps.containsKey(userId) -> handleJoinRequestFlow(bot, userId, text)
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

        // Обработчик потока записи в группу
        private suspend fun handleJoinRequestFlow(bot: VkClient, userId: Long, text: String) {
            val step = joinRequestSteps[userId] ?: return

            when (step) {
                1 -> {
                    // Выбор ребенка
                    try {
                        val trimmed = text.trim()
                        val num = trimmed.toInt()
                        val children = dbService.getChildrenByParentVkId(userId)
                        if (num < 1 || num > children.size) {
                            sendText(bot, userId, "Неверный номер. Попробуйте снова.")
                            return
                        }

                        val child = children[num - 1]
                        val childId = (child["id"] as Number).toLong()

                        // Ищем подходящие группы
                        val suitableGroups = dbService.findSuitableGroupsForChild(childId)

                        if (suitableGroups.isEmpty()) {
                            sendText(bot, userId, "К сожалению, сейчас нет подходящих групп для вашего ребенка.")
                            joinRequestSteps.remove(userId)
                            joinRequestTempData.remove(userId)
                            return
                        }

                        // Формируем сообщение со списком групп и их расписанием
                        val sb = StringBuilder("Вот подходящие группы:\n\n")
                        suitableGroups.forEachIndexed { index, group ->
                            sb.append("${index + 1}. Группа №${group["number"]} \"${group["name"]}\"\n")

                            // Добавляем расписание (Пн-Пт)
                            val days = listOf(
                                "Пн" to Pair(group["day_1_start"], group["day_1_end"]),
                                "Вт" to Pair(group["day_2_start"], group["day_2_end"]),
                                "Ср" to Pair(group["day_3_start"], group["day_3_end"]),
                                "Чт" to Pair(group["day_4_start"], group["day_4_end"]),
                                "Пт" to Pair(group["day_5_start"], group["day_5_end"])
                            )

                            days.forEach { (dayName, times) ->
                                if (times.first != null && times.second != null) {
                                    sb.append("   $dayName: ${times.first}-${times.second}\n")
                                }
                            }
                            sb.append("\n")
                        }

                        sb.append("Напишите номер группы (1 или 2), в которую хотите записаться:")
                        sendText(bot, userId, sb.toString())

                        // Сохраняем ID ребенка и список групп для следующего шага
                        val tempData = mutableMapOf<String, Any>()
                        tempData["childId"] = childId
                        tempData["groups"] = suitableGroups
                        joinRequestTempData[userId] = tempData

                        joinRequestSteps[userId] = 2

                    } catch (e: NumberFormatException) {
                        sendText(bot, userId, "Пожалуйста, введите номер ребенка цифрой (например, 1, 2 или 3).")
                    } catch (e: Exception) {
                        sendText(bot, userId, "Произошла ошибка. Попробуйте еще раз.")
                        LOGGER.severe("Ошибка в шаге 1 handleJoinRequestFlow: ${e.message}")
                    }
                }

                2 -> {
                    // Выбор группы и создание заявки
                    try {
                        val trimmed = text.trim()
                        val num = trimmed.toInt()
                        val tempData = joinRequestTempData[userId]
                        if (tempData == null) {
                            sendText(bot, userId, "Произошла ошибка. Попробуйте начать заново командой 'записаться'.")
                            joinRequestSteps.remove(userId)
                            joinRequestTempData.remove(userId)
                            return
                        }

                        val groups = tempData["groups"] as? List<Map<String, Object>>
                        if (groups == null || num < 1 || num > groups.size) {
                            sendText(bot, userId, "Неверный номер группы. Попробуйте снова (введите 1 или 2).")
                            return
                        }

                        val childId = tempData["childId"] as Long
                        val group = groups[num - 1] // Берем группу по индексу в списке
                        val groupId = (group["id"] as Number).toLong() // Берем реальный ID группы из базы

                        // Создаем заявку с реальным ID группы
                        dbService.createJoinRequest(userId, childId, groupId)

                        sendText(bot, userId, "✅ Заявка на запись создана! Администратор рассмотрит её в ближайшее время.")

                        // Очищаем состояние
                        joinRequestSteps.remove(userId)
                        joinRequestTempData.remove(userId)

                    } catch (e: NumberFormatException) {
                        sendText(bot, userId, "Пожалуйста, введите номер группы цифрой (1 или 2).")
                    } catch (e: Exception) {
                        sendText(bot, userId, "Произошла ошибка. Попробуйте еще раз.")
                        LOGGER.severe("Ошибка в шаге 2 handleJoinRequestFlow: ${e.message}")
                        e.printStackTrace()
                    }
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
                "квитанция" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        paymentReceiptHandler.startUpload(userId)
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
                            // Запускаем редактирование ребенка
                            childEditHandler.startEditingChild(userId, (children[0]["id"] as Long), children[0])
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
                "записаться" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                        return
                    }
                    val children = dbService.getChildrenByParentVkId(userId)
                    if (children.isEmpty()) {
                        sendText(bot, userId, "У вас пока нет детей. Добавьте ребенка командой 'добавитьребенка'.")
                        return
                    }
                    val sb = StringBuilder("Для какого ребенка ищем группу?\n")
                    children.forEachIndexed { i, c ->
                        sb.append("${i + 1}. ${c["lastName"]} ${c["firstName"]} (${c["age"]} лет, навык: ${c["skill"]})\n")
                    }
                    sendText(bot, userId, sb.toString())
                    joinRequestSteps[userId] = 1
                }
                "договор", "согласие", "правила", "квитанция" -> {
                    sendText(bot, userId, "Отправка документов временно отключена.")
                }
                else -> {
                    if (text.matches(Regex("\\d+"))) {
                        val num = text.toInt()
                        val children = dbService.getChildrenByParentVkId(userId)
                        if (num > 0 && num <= children.size) {
                            val child = children[num - 1]
                            childEditHandler.startEditingChild(userId, (child["id"] as Number).toLong(), child)
                            sendText(bot, userId, "Редактирование: ${child["lastName"]} ${child["firstName"]}.\nВведите новую фамилию:")
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