package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.sashil.admin.model.JoinRequestNotification;
import java.util.List;

@Repository
public interface JoinRequestNotificationRepository extends JpaRepository<JoinRequestNotification, Long> {
    List<JoinRequestNotification> findByIsSentFalseOrderByCreatedAtAsc();
}