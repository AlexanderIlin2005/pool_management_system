package ru.sashil.bot.util

import java.util.logging.Logger

/**
 * Утилита для отправки WebSocket уведомлений из Kotlin кода.
 * Использует рефлексию для вызова Spring сервиса.
 */
object WebSocketNotifier {
    private val LOGGER = Logger.getLogger(WebSocketNotifier::class.java.name)

    /**
     * Отправляет WebSocket уведомление через Spring сервис.
     * @param eventType Тип события (например, "BROADCAST_COMPLETED")
     */
    fun sendWebSocketNotification(eventType: String) {
        try {
            // Пытаемся получить SpringContextHolder
            val contextHolderClass = Class.forName("ru.sashil.common.util.SpringContextHolder")
            val getBeanMethod = contextHolderClass.getMethod("getBean", Class::class.java)

            // Получаем WsNotificationService
            val wsServiceClass = Class.forName("ru.sashil.admin.service.WsNotificationService")
            val wsService = getBeanMethod.invoke(null, wsServiceClass)

            if (wsService != null) {
                // Вызываем метод sendUpdateNotification
                val method = wsServiceClass.getMethod("sendUpdateNotification", String::class.java)
                method.invoke(wsService, eventType)
                LOGGER.info("✅ WebSocket уведомление отправлено: $eventType")
            }
        } catch (e: ClassNotFoundException) {
            // SpringContextHolder или WsNotificationService не найдены (бота запущен без Spring)
            LOGGER.fine("WebSocket уведомление не отправлено (Spring контекст не доступен): ${e.message}")
        } catch (e: Exception) {
            LOGGER.warning("Ошибка отправки WebSocket уведомления: ${e.message}")
        }
    }
}