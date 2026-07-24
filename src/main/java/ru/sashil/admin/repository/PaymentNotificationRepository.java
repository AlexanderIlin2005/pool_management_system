package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.PaymentNotification;

import java.time.LocalDate;
import java.util.List;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotification, Long> {

    List<PaymentNotification> findByIsSentFalseOrderByCreatedAtAsc();

    List<PaymentNotification> findByParentVkIdAndIsSentFalse(Long parentVkId);

    boolean existsByParentVkIdAndChildIdAndMonthYearAndNotificationType(
            Long parentVkId, Long childId, LocalDate monthYear, String notificationType);
}