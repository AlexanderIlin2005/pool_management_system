package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "absence_notifications", schema = "pool")
@Data
public class AbsenceNotification {

    public enum AbsenceType {
        SICK("Болезнь (со справкой)"),
        UNWELL("Недомогание (без справки)"),
        OTHER("Другая причина");

        private final String displayName;

        AbsenceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Status {
        PENDING("Ожидает"),
        READ("Прочитано"),
        PROCESSED("Обработано");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private Child child;

    @Column(name = "absence_type", nullable = false)
    private String absenceType;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "certificate_url")
    private String certificateUrl;

    @Column(name = "certificate_file_name")
    private String certificateFileName;

    @Column(name = "status")
    private String status = "PENDING";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private AdminUser processedBy;
}