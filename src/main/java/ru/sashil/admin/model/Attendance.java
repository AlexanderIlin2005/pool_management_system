package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance", schema = "pool")
@Data
public class Attendance {

    public enum Status {
        PRESENT("Присутствует"),
        ABSENT("Отсутствует"),
        SICK("Болеет"),
        EXCUSED("Уважительная причина");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }


        public static Status fromLabel(String label) {
            for (Status s : values()) {
                if (s.label.equals(label) || s.name().equals(label)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown status: " + label);
        }
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


    @Convert(converter = StatusConverter.class)
    @Column(nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by")
    private AdminUser markedBy;

    @Column(name = "marked_at")
    private LocalDateTime markedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;


    @Converter
    public static class StatusConverter implements AttributeConverter<Status, String> {
        @Override
        public String convertToDatabaseColumn(Status attribute) {
            return attribute != null ? attribute.name() : null;
        }

        @Override
        public Status convertToEntityAttribute(String dbData) {
            return dbData != null ? Status.fromLabel(dbData) : null;
        }
    }
}