package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalTime;

@Entity
@Table(name = "groups", schema = "pool")
@Data
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Integer number;

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "day_of_week_1")
    private Integer dayOfWeek1;

    @Column(name = "start_time_1")
    private LocalTime startTime1;

    @Column(name = "end_time_1")
    private LocalTime endTime1;

    @Column(name = "day_of_week_2")
    private Integer dayOfWeek2;

    @Column(name = "start_time_2")
    private LocalTime startTime2;

    @Column(name = "end_time_2")
    private LocalTime endTime2;

    private Integer capacity;

    @ManyToOne
    @JoinColumn(name = "pool_id")
    private Pool pool;
}