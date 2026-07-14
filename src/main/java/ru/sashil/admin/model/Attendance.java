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

        // Метод для поиска по русской метке (из формы) или по имени (из БД)
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

    // Используем кастомный конвертер
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

    /**
     * Конвертер теперь сохраняет в БД имя константы (PRESENT),
     * а читает либо имя, либо русскую метку.
     */
    @Converter
    public static class StatusConverter implements AttributeConverter<Status, String> {
        @Override
        public String convertToDatabaseColumn(Status attribute) {
            // ВАЖНО: Сохраняем в БД английское имя константы (PRESENT), чтобы пройти CHECK constraint
            return attribute != null ? attribute.name() : null;
        }

        @Override
        public Status convertToEntityAttribute(String dbData) {
            // Читаем из БД. Там теперь лежит "PRESENT".
            // Но метод fromLabel умеет искать и по имени, и по метке, так что всё сработает.
            return dbData != null ? Status.fromLabel(dbData) : null;
        }
    }
}