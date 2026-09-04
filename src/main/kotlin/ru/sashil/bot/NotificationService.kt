package ru.sashil.bot

import io.github.blackbaroness.vk.VkClient
import ru.sashil.common.service.DatabaseService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.logging.Logger

class NotificationService(
    private val dbService: DatabaseService,
    private val vkClient: VkClient
) {
    private val logger = Logger.getLogger(NotificationService::class.java.name)

    /**
     * Ежедневная проверка (вечером) — только напоминания о завтрашних занятиях
     */
    suspend fun checkDailyNotifications() {
        logger.info("Запуск ежедневной проверки уведомлений...")
        val now = LocalDateTime.now()
        val tomorrow = now.toLocalDate().plusDays(1)

        // Проверяем, что сейчас вечер (18:00 - 21:00)
        val currentTime = now.toLocalTime()
        val isEvening = currentTime.isAfter(LocalTime.of(18, 0)) &&
                currentTime.isBefore(LocalTime.of(21, 0))

        if (!isEvening) {
            logger.info("Сейчас не вечернее время (${currentTime}), пропускаем отправку уведомлений о занятиях")
            return
        }

        logger.info("Вечернее время, отправляем уведомления о завтрашних занятиях...")

        val parents = getAllParents()

        for (parent in parents) {
            val parentId = parent["id"] as Long
            val vkId = parent["vk_id"] as Long

            try {
                if (dbService.isRegularNotificationsEnabled(vkId)) {
                    processTomorrowNotifications(vkId, parentId, tomorrow)
                }
            } catch (e: Exception) {
                logger.severe("Ошибка при обработке уведомлений для родителя $vkId: ${e.message}")
            }
        }

        logger.info("Ежедневная проверка уведомлений завершена.")
    }

    /**
     * Мгновенная отправка всех остальных уведомлений (каждую минуту)
     */
    suspend fun sendInstantNotifications() {
        sendPendingSkillNotifications()
        sendPendingJoinRequestNotifications()
        sendPendingPaymentNotifications()
        sendPendingChildUpdateNotifications()
        sendPendingMessagesToParents()
        sendPendingGroupMemberNotifications()
    }

    /**
     * Отправляет уведомления о завтрашних занятиях
     */
    private suspend fun processTomorrowNotifications(vkId: Long, parentId: Long, tomorrow: LocalDate) {
        val tomorrowLessons = dbService.getChildrenScheduleForDate(parentId, tomorrow)
        for (lesson in tomorrowLessons) {
            if (lesson["startTime"] != null) {
                if (!dbService.hasNotificationBeenSent(parentId, lesson["childId"] as Long, "TOMORROW", tomorrow)) {
                    sendTomorrowReminder(vkId, lesson, tomorrow)
                    dbService.logNotificationSent(parentId, lesson["childId"] as Long, "TOMORROW", tomorrow)
                }
            }
        }
    }

    /**
     * Отправляет сообщения от админов/тренеров родителям
     */
    suspend fun sendPendingMessagesToParents() {
        try {
            val messages = dbService.getPendingMessagesForParents()
            for (msg in messages) {
                val parentVkId = msg["parent_vk_id"] as Long
                val messageText = msg["message_text"] as String
                val fromUserType = msg["from_user_type"] as String
                val fromUserId = msg["from_user_id"] as Long?
                val senderName = msg["sender_name"] as String?
                val messageId = msg["id"] as Long

                val fromName = when (fromUserType) {
                    "ADMIN" -> "администратора"
                    "COACH" -> {
                        if (senderName != null && senderName.isNotEmpty()) {
                            "тренера $senderName"
                        } else {
                            "тренера"
                        }
                    }
                    else -> "сотрудника"
                }

                val fullMessage = "📨 Вы получили сообщение от $fromName:\n\n$messageText"

                try {
                    sendMessage(parentVkId, fullMessage)
                    dbService.markParentMessageSent(messageId)
                    logger.info("Сообщение #$messageId отправлено родителю $parentVkId от $fromName")
                } catch (e: Exception) {
                    logger.severe("Ошибка отправки сообщения родителю $parentVkId: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.severe("Ошибка в отправке сообщений родителям: ${e.message}")
        }
    }

    private suspend fun sendPendingChildUpdateNotifications() {
        val notifications = dbService.getPendingChildUpdateNotifications()
        for (notif in notifications) {
            val parentVkId = notif["parent_vk_id"] as Long
            val message = notif["message_text"] as String
            val notifId = notif["id"] as Long

            try {
                sendMessage(parentVkId, message)
                dbService.markChildUpdateNotificationSent(notifId)
                logger.info("Уведомление об изменении данных ребенка #$notifId отправлено родителю $parentVkId")
            } catch (e: Exception) {
                logger.severe("Не удалось отправить уведомление об изменении данных пользователю $parentVkId: ${e.message}")
            }
        }
    }

    private suspend fun sendPendingPaymentNotifications() {
        val notifications = dbService.getPendingPaymentNotifications()
        for (notif in notifications) {
            val parentVkId = notif["parent_vk_id"] as Long
            val message = notif["message_text"] as String
            val notifId = notif["id"] as Long

            try {
                sendMessage(parentVkId, message)
                dbService.markPaymentNotificationSent(notifId)
                logger.info("Уведомление об оплате #$notifId отправлено родителю $parentVkId")
            } catch (e: Exception) {
                logger.severe("Не удалось отправить уведомление об оплате пользователю $parentVkId: ${e.message}")
            }
        }
    }

    private suspend fun sendPendingJoinRequestNotifications() {
        val notifications = dbService.getPendingJoinRequestNotifications()
        for (notif in notifications) {
            val id = notif["id"] as Long
            val parentVkId = notif["parent_vk_id"] as Long
            val messageText = notif["message_text"] as String

            try {
                sendMessage(parentVkId, messageText)
                dbService.markJoinRequestNotificationSent(id)
                logger.info("Уведомление о заявке #$id отправлено родителю $parentVkId")
            } catch (e: Exception) {
                logger.severe("Не удалось отправить уведомление о заявке #$id пользователю $parentVkId: ${e.message}")
            }
        }
    }

    private suspend fun sendPendingSkillNotifications() {
        val notifications = dbService.getPendingSkillNotifications()
        for (notif in notifications) {
            val vkId = notif["vk_id"] as Long
            val childName = notif["child_name"] as String
            val oldSkill = notif["old_skill"] as String
            val newSkill = notif["new_skill"] as String
            val notifId = notif["id"] as Long

            val text = "Навык плавания ребенка $childName был изменен с '$oldSkill' на '$newSkill'."

            try {
                sendMessage(vkId, text)
                dbService.markSkillNotificationSent(notifId)
                logger.info("Уведомление об изменении навыка отправлено родителю $vkId")
            } catch (e: Exception) {
                logger.severe("Не удалось отправить уведомление об изменении навыка пользователю $vkId: ${e.message}")
            }
        }
    }

    private suspend fun sendPendingGroupMemberNotifications() {
        val notifications = dbService.getPendingGroupMemberNotifications()
        for (notif in notifications) {
            val parentVkId = notif["parent_vk_id"] as Long
            val message = notif["message_text"] as String
            val notifId = notif["id"] as Long

            try {
                sendMessage(parentVkId, message)
                dbService.markGroupMemberNotificationSent(notifId)
                logger.info("Уведомление о членстве в группе #$notifId отправлено родителю $parentVkId")
            } catch (e: Exception) {
                logger.severe("Не удалось отправить уведомление о членстве в группе пользователю $parentVkId: ${e.message}")
            }
        }
    }

    private suspend fun sendTomorrowReminder(vkId: Long, lesson: Map<String, Any>, date: LocalDate) {
        val time = lesson["startTime"].toString().substring(0, 5)
        val childName = getChildName(lesson["childId"] as Long)

        val text = "Уведомление о занятии в бассейне: $childName завтра в $time записан(а) в бассейн. Ждем на занятии!"

        sendMessage(vkId, text)
    }

    private fun getChildName(childId: Long): String {
        var name = "ребенка"
        val sql = "SELECT first_name, last_name FROM pool.children WHERE id = ?"
        dbService.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, childId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val firstName = rs.getString("first_name")
                        val lastName = rs.getString("last_name")
                        if (firstName != null && lastName != null) {
                            name = "$firstName $lastName"
                        } else if (firstName != null) {
                            name = firstName
                        }
                    }
                }
            }
        }
        return name
    }

    private fun getAllParents(): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val sql = "SELECT id, vk_id FROM pool.parents"
        dbService.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(mapOf("id" to rs.getLong("id"), "vk_id" to rs.getLong("vk_id")))
                    }
                }
            }
        }
        return result
    }

    private suspend fun sendMessage(vkId: Long, text: String) {
        try {
            vkClient.messages.send(vkId) {
                message = text
                randomId = (Math.random() * Int.MAX_VALUE).toInt()
            }
        } catch (e: Exception) {
            logger.severe("Не удалось отправить сообщение пользователю $vkId: ${e.message}")
        }
    }
}