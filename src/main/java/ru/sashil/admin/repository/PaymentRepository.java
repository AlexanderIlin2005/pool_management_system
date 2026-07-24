package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sashil.admin.model.Payment;
import ru.sashil.admin.model.Child;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByChildIdAndMonthYear(Long childId, LocalDate monthYear);

    List<Payment> findByChildIdOrderByMonthYearDesc(Long childId);

    List<Payment> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT p FROM Payment p WHERE p.monthYear >= :startMonth AND p.monthYear <= :endMonth ORDER BY p.monthYear, p.child.lastName, p.child.firstName")
    List<Payment> findPaymentsInPeriod(@Param("startMonth") LocalDate startMonth, @Param("endMonth") LocalDate endMonth);

    @Query("SELECT p FROM Payment p WHERE p.monthYear < :currentMonth AND p.isPaid = false AND p.status != 'REJECTED'")
    List<Payment> findOverduePayments(@Param("currentMonth") LocalDate currentMonth);

    @Query("SELECT p FROM Payment p WHERE p.monthYear = :month AND p.isPaid = false")
    List<Payment> findUnpaidForMonth(@Param("month") LocalDate month);
}