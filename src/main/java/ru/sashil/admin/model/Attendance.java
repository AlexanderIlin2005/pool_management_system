package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance", schema = "pool")
@Data
public class Attendance {
    public enum Status {
        PRESENT("Присутствует"), ABSENT("Отсутствует"), SICK("Болеет"), EXCUSED("Уважительная причина");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private PoolLesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by")
    private AdminUser markedBy;

    @Column(name = "marked_at")
    private LocalDateTime markedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;
}