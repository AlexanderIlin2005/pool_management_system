package ru.sashil.admin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleSlot {
    private Long groupId;
    private Long lessonId; // <-- ДОБАВЛЕНО ПОЛЕ ДЛЯ ССЫЛКИ НА ЗАНЯТИЕ В БД
    private String groupName;
    private Integer groupNumber;
    private LocalTime startTime;
    private LocalTime endTime;
    private String trainerName;
    private String poolName;

    // Позиционирование по вертикали
    private double topPercent;
    private double heightPercent;

    // Позиционирование по горизонтали (для разделения колонок)
    private double leftPercent;
    private double widthPercent;

    private boolean isOverlapping;
}