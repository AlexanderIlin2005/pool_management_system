package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WsNotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Отправляет уведомление всем подключенным клиентам о необходимости обновить данные.
     * Тип события может быть: 'DATA_CHANGED', 'GROUP_UPDATED', 'PARENT_DELETED' и т.д.
     */
    public void sendUpdateNotification(String eventType) {
        messagingTemplate.convertAndSend("/topic/updates", eventType);
    }
}