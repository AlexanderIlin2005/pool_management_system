package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.AbsenceNotification;
import java.util.List;

public interface AbsenceNotificationRepository extends JpaRepository<AbsenceNotification, Long> {
    List<AbsenceNotification> findByStatusOrderByCreatedAtDesc(String status);
    List<AbsenceNotification> findByChildId(Long childId);
    List<AbsenceNotification> findByParentId(Long parentId);
}