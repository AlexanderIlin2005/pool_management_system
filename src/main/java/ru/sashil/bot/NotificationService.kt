package ru.sashil.bot

import io.github.blackbaroness.vk.VkClient
import ru.sashil.common.service.DatabaseService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class NotificationService(
    private val dbService: DatabaseService,
    private val vkClient: VkClient
) {
    private val logger = Logger.getLogger(NotificationService::class.java.name)

    /**
     * Основной метод, который нужно вызывать по расписанию (например, раз в час).
     */
    suspend fun checkAndSendNotifications() {
        logger.info("Запуск проверки уведомлений...")
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        
        val parents = getAllParents()

        for (parent in parents) {
            val parentId = parent["id"] as Long
            val vkId = parent["vk_id"] as Long

            try {
                
                if (dbService.isRegularNotificationsEnabled(vkId)) {
                    processRegularNotifications(vkId, parentId, today, tomorrow)
                }

                
                

            } catch (e: Exception) {
                logger.severe("Ошибка при обработке уведомлений для родителя $vkId: ${e.message}")
            }
        }

        // Отправка уведомлений об изменении навыков
        sendPendingSkillNotifications()

        logger.info("Проверка уведомлений завершена.")
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

    private suspend fun processRegularNotifications(vkId: Long, parentId: Long, today: LocalDate, tomorrow: LocalDate) {
        
        val tomorrowLessons = dbService.getChildrenScheduleForDate(parentId, tomorrow)
        for (lesson in tomorrowLessons) {
            
            if (lesson["startTime"] != null) {
                if (!dbService.hasNotificationBeenSent(parentId, lesson["childId"] as Long, "TOMORROW", tomorrow)) {
                    sendTomorrowReminder(vkId, lesson, tomorrow)
                    dbService.logNotificationSent(parentId, lesson["childId"] as Long, "TOMORROW", tomorrow)
                }
            }
        }

        
        val todayLessons = dbService.getChildrenScheduleForDate(parentId, today)
        for (lesson in todayLessons) {
            if (lesson["startTime"] != null) {
                if (!dbService.hasNotificationBeenSent(parentId, lesson["childId"] as Long, "TODAY", today)) {
                    sendTodayReminder(vkId, lesson, today)
                    dbService.logNotificationSent(parentId, lesson["childId"] as Long, "TODAY", today)
                }
            }
        }
    }

    private suspend fun sendTomorrowReminder(vkId: Long, lesson: Map<String, Any>, date: LocalDate) {
        val time = lesson["startTime"].toString().substring(0, 5)

        
        val groupNumber = lesson["groupNumber"]?.toString() ?: lesson["groupName"].toString()

        val childName = getChildName(lesson["childId"] as Long)

        val text = "Добрый день! Напоминаем, что завтра у $childName занятие в бассейне. Группа $groupNumber в $time.\nЖдем на занятии!"
        sendMessage(vkId, text)
    }

    private suspend fun sendTodayReminder(vkId: Long, lesson: Map<String, Any>, date: LocalDate) {
        val time = lesson["startTime"].toString().substring(0, 5)

        
        val groupNumber = lesson["groupNumber"]?.toString() ?: lesson["groupName"].toString()

        val childName = getChildName(lesson["childId"] as Long)

        val text = "Добрый день! Напоминаем, что сегодня у $childName занятие в бассейне. Группа $groupNumber в $time.\nЖдем на занятии!"
        sendMessage(vkId, text)
    }

    private fun getChildName(childId: Long): String {
        var name = "ребенка"
        val sql = "SELECT first_name FROM pool.children WHERE id = ?"
        dbService.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, childId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val firstName = rs.getString("first_name")
                        if (firstName != null) {
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