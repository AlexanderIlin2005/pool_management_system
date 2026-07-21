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
    private Long lessonId;
    private String groupName;
    private Integer groupNumber;
    private LocalTime startTime;
    private LocalTime endTime;
    private String trainerName;
    private String poolName;


    private double topPercent;
    private double heightPercent;


    private double leftPercent;
    private double widthPercent;

    private boolean isOverlapping;
}