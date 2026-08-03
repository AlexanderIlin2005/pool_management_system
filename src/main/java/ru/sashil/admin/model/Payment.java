package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "payments", schema = "pool")
@Data
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "month_year", nullable = false)
    private LocalDate monthYear;

    @Column(name = "is_paid")
    private Boolean isPaid = false;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "amount")
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "total_paid")
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "receipt_file_url")
    private String receiptFileUrl;

    @Column(name = "receipt_original_name")
    private String receiptOriginalName;

    @Column(name = "status")
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private AdminUser verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "amount_change_comment", columnDefinition = "TEXT")
    private String amountChangeComment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "amount_history", columnDefinition = "jsonb")
    private List<Map<String, Object>> amountHistory = new java.util.ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isFullyPaid() {
        if (amount == null || totalPaid == null) return false;
        return totalPaid.compareTo(amount) >= 0;
    }

    public BigDecimal getRemainingAmount() {
        if (amount == null || totalPaid == null) return BigDecimal.ZERO;
        return amount.subtract(totalPaid).max(BigDecimal.ZERO);
    }
}