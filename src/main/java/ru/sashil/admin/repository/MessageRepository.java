package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.Message;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Для админа - все непрочитанные
    @Query("SELECT m FROM Message m WHERE m.toUserType = 'ADMIN' AND m.status = 'PENDING' ORDER BY m.createdAt DESC")
    List<Message> findPendingForAdmins();

    // Для админа - все активные (не отвеченные)
    @Query("SELECT m FROM Message m WHERE m.toUserType = 'ADMIN' AND m.status != 'REPLIED' ORDER BY m.createdAt DESC")
    List<Message> findActiveForAdmins();

    // Для тренера - все непрочитанные
    @Query("SELECT m FROM Message m WHERE m.toUserId = :coachId AND m.status = 'PENDING' ORDER BY m.createdAt DESC")
    List<Message> findPendingForCoach(Long coachId);

    // Для тренера - все активные (не отвеченные)
    @Query("SELECT m FROM Message m WHERE m.toUserId = :coachId AND m.status != 'REPLIED' ORDER BY m.createdAt DESC")
    List<Message> findActiveForCoach(Long coachId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = 'READ', m.readAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    void markAsRead(Long messageId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = 'REPLIED', m.repliedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    void markAsReplied(Long messageId);

    @Query("SELECT m FROM Message m WHERE m.parentMessageId = :parentId ORDER BY m.createdAt ASC")
    List<Message> findReplies(Long parentId);
}