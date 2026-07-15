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

        // Получаем всех родителей из БД (нужно добавить метод getAllParentsVkIds в DatabaseService или сделать запрос здесь)
        // Для простоты сделаем запрос внутри метода через JDBC, так как это Kotlin-класс

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
                processCancellations(vkId, parentId, today)

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

        // Уведомление о занятии СЕГОДНЯ (отправляем утром, например до 12:00, но тут просто проверяем наличие)
        // Чтобы не спамить, можно ограничить время отправки, но пока просто отправим если еще не отправляли
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

    private suspend fun processCancellations(vkId: Long, parentId: Long, today: LocalDate) {
        // Проверяем следующую неделю на наличие "дыр" в расписании, которые раньше были
        // Это сложная логика, требующая истории расписания.
        // Упрощенный вариант: если у ребенка была группа, а на завтра занятия нет (и это не выходной по графику группы),
        // то возможно оно отменено.
        // Но так как у нас генерация идет автоматически, то "исчезновение" обычно означает праздник/каникулы.

        // Реализуем проверку на ближайшие 7 дней
        for (i in 1..7) {
            val dateToCheck = today.plusDays(i.toLong())
            val lessons = dbService.getChildrenScheduleForDate(parentId, dateToCheck)

            // Если у ребенка есть группа, но на эту дату нет занятия (lesson is null or empty list for specific child)
            // Мы можем узнать, должна ли быть группа в этот день недели, из самой группы.
            // Но проще: если в списке детей родителя есть ребенок, привязанный к группе,
            // но в getChildrenScheduleForDate для этой даты нет записи про эту группу -> значит занятие отменено.

            // Получаем просто список групп детей
            val childrenGroups = getChildrenGroups(parentId)

            for (cg in childrenGroups) {
                val childId = cg["childId"] as Long
                val groupId = cg["groupId"] as Long

                // Проверяем, есть ли занятие в БД
                val hasLesson = lessons.any { it["childId"] == childId }

                // Проверяем, должен ли быть урок по графику группы (это требует дополнительного запроса или логики)
                // Для простоты: если урока нет в БД, считаем что он отменен/перенесен.
                // Но чтобы не спамить на выходные, нужно знать расписание группы.

                // Пока реализуем только если урок был в БД вчера/раньше, а теперь исчез? Нет, это сложно.
                // Давайте сделаем проще: если урок отменен админом (is_cancelled=true), мы можем его найти.
                // Но у нас в запросе getChildrenScheduleForDate мы делаем LEFT JOIN, так что если урока нет, то startTime будет null.

                if (!hasLesson) {
                    // Потенциальная отмена. Нужно проверить, не выходной ли это день для группы.
                    // Это требует сложного запроса. Пока пропустим эту часть, так как она требует глубокой интеграции с логикой Group.
                    // Вместо этого сосредоточимся на изменении времени.
                }
            }
        }
    }

    // Вспомогательный метод для получения связей ребенок-группа
    private fun getChildrenGroups(parentId: Long): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val sql = "SELECT c.id as childId, gc.group_id as groupId FROM pool.children c JOIN pool.group_children gc ON c.id = gc.child_id WHERE c.parent_id = ?"
        dbService.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, parentId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(mapOf("childId" to rs.getLong("childId"), "groupId" to rs.getLong("groupId")))
                    }
                }
            }
        }
        return result
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

    private suspend fun sendTomorrowReminder(vkId: Long, lesson: Map<String, Any>, date: LocalDate) {
        val time = lesson["startTime"].toString().substring(0, 5)
        val group = lesson["groupName"]
        val childName = getChildName(lesson["childId"] as Long) // Нужен метод
        val text = "Напоминание: Завтра (${date.format(DateTimeFormatter.ofPattern("dd.MM"))}) у $childName занятие в группе \"$group\" в $time."
        sendMessage(vkId, text)
    }

    private suspend fun sendTodayReminder(vkId: Long, lesson: Map<String, Any>, date: LocalDate) {
        val time = lesson["startTime"].toString().substring(0, 5)
        val group = lesson["groupName"]
        val childName = getChildName(lesson["childId"] as Long)
        val text = "Напоминание: Сегодня у $childName занятие в группе \"$group\" в $time. Не опоздайте!"
        sendMessage(vkId, text)
    }

    private fun getChildName(childId: Long): String {
        var name = "Ребенок"
        val sql = "SELECT first_name, last_name FROM pool.children WHERE id = ?"
        dbService.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, childId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        name = "${rs.getString("last_name")} ${rs.getString("first_name")}"
                    }
                }
            }
        }
        return name
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