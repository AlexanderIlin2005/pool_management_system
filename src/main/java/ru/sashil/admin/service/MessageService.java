package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.Message;
import ru.sashil.admin.repository.MessageRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

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

    /**
     * Получает все непрочитанные сообщения для родителей
     */
    public List<Message> getPendingMessagesForParents() {
        return messageRepository.findPendingForParents();
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
     * Сохраняется в таблицу messages
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
        reply.setToUserId(original.getFromUserId()); // VK ID родителя
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
    }
}