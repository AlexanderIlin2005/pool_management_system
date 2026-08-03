package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.Message;
import ru.sashil.admin.repository.MessageRepository;
import ru.sashil.common.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private DatabaseService databaseService;

    public List<Message> getPendingMessagesForAdmin() {
        return messageRepository.findPendingForAdmins();
    }

    public List<Message> getActiveMessagesForAdmin() {
        return messageRepository.findActiveForAdmins();
    }

    public List<Message> getPendingMessagesForCoach(Long coachId) {
        return messageRepository.findPendingForCoach(coachId);
    }

    public List<Message> getActiveMessagesForCoach(Long coachId) {
        return messageRepository.findActiveForCoach(coachId);
    }

    @Transactional
    public void markMessageAsRead(Long messageId) {
        messageRepository.markAsRead(messageId);
    }

    @Transactional
    public void markMessageAsReplied(Long messageId) {
        messageRepository.markAsReplied(messageId);
    }

    /**
     * Отправка ответа родителю от тренера/администратора
     */
    @Transactional
    public void replyToMessage(Long parentMessageId, Long adminId, String userType, String replyText) {
        // Получаем исходное сообщение
        Message original = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new RuntimeException("Сообщение не найдено"));

        // Создаем ответ
        Message reply = new Message();
        reply.setFromUserId(adminId);
        reply.setFromUserType(userType);
        reply.setToUserId(original.getFromUserId());
        reply.setToUserType("PARENT");
        reply.setChild(original.getChild());
        reply.setGroup(original.getGroup());
        reply.setMessageText(replyText);
        reply.setParentMessageId(parentMessageId);
        reply.setStatus("PENDING");
        reply.setCreatedAt(LocalDateTime.now());

        messageRepository.save(reply);

        // Отмечаем исходное сообщение как отвеченное
        markMessageAsReplied(parentMessageId);

        // Отправляем уведомление родителю через VK бота
        try {
            String notificationMessage = "Вы получили ответ от " +
                    (userType.equals("ADMIN") ? "администратора" : "тренера") +
                    ":\n\n" + replyText;

            String sql = "INSERT INTO pool.payment_notifications " +
                    "(parent_vk_id, message_text, notification_type, created_at, is_sent) " +
                    "VALUES (?, ?, 'MESSAGE_REPLY', CURRENT_TIMESTAMP, FALSE)";

            try (Connection conn = databaseService.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, original.getFromUserId());
                stmt.setString(2, notificationMessage);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Object> getMessageWithReplies(Long messageId) {
        return null;
    }
}