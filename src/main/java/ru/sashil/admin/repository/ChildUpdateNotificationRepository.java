package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.ChildUpdateNotification;
import java.util.List;

public interface ChildUpdateNotificationRepository extends JpaRepository<ChildUpdateNotification, Long> {
    List<ChildUpdateNotification> findByIsSentFalseOrderByCreatedAtAsc();
}