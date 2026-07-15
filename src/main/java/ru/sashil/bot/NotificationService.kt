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

        // Получаем всех родителей из БД
        val parents = getAllParents()

        for (parent in parents) {
            val parentId = parent["id"] as Long
            val vkId = parent["vk_id"] as Long

            try {
                // 1. Проверка регулярных уведомлений (если включены)
                if (dbService.isRegularNotificationsEnabled(vkId)) {
                    processRegularNotifications(vkId, parentId, today, tomorrow)
                }

                // 2. Проверка отмен занятий (на ближайшую неделю) - отправляется всегда
                // (Логика проверок отмен пока упрощена, но место под нее есть)

            } catch (e: Exception) {
                logger.severe("Ошибка при обработке уведомлений для родителя $vkId: ${e.message}")
            }
        }
        logger.info("Проверка уведомлений завершена.")
    }

    private suspend fun processRegularNotifications(vkId: Long, parentId: Long, today: LocalDate, tomorrow: LocalDate) {
        // Уведомление о занятии ЗАВТРА (отправляем сегодня)
        val tomorrowLessons = dbService.getChildrenScheduleForDate(parentId, tomorrow)
        for (lesson in tomorrowLessons) {
            // Если занятие есть (не отменено/удалено)
            if (lesson["startTime"] != null) {
                if (!dbService.hasNotificationBeenSent(parentId, lesson["childId"] as Long, "TOMORROW", tomorrow)) {
                    sendTomorrowReminder(vkId, lesson, tomorrow)
                    dbService.logNotificationSent(parentId, lesson["childId"] as Long, "TOMORROW", tomorrow)
                }
            }
        }

        // Уведомление о занятии СЕГОДНЯ (отправляем утром)
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

        // ИСПРАВЛЕНИЕ: Берем номер и приводим к строке. Если номера нет (редкий случай), берем имя.
        val groupNumber = lesson["groupNumber"]?.toString() ?: lesson["groupName"].toString()

        val childName = getChildName(lesson["childId"] as Long)

        val text = "Добрый день! Напоминаем, что завтра у $childName занятие в бассейне. Группа $groupNumber в $time.\nЖдем на занятии!"
        sendMessage(vkId, text)
    }

    private suspend fun sendTodayReminder(vkId: Long, lesson: Map<String, Any>, date: LocalDate) {
        val time = lesson["startTime"].toString().substring(0, 5)

        // ИСПРАВЛЕНИЕ: Аналогично для сегодня
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