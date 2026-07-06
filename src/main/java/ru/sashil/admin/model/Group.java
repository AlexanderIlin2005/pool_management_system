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

    @Column(nullable = false)
    private String name;

    private Integer capacity;

    @ManyToOne
    @JoinColumn(name = "pool_id")
    private Pool pool;

    // Связь с тренером (AdminUser с ролью COACH)
    @ManyToOne
    @JoinColumn(name = "trainer_id")
    private AdminUser trainer;

    // Время для 7 дней недели
    @Column(name = "day_1_start") private LocalTime day1Start;
    @Column(name = "day_1_end")   private LocalTime day1End;
    @Column(name = "day_2_start") private LocalTime day2Start;
    @Column(name = "day_2_end")   private LocalTime day2End;
    @Column(name = "day_3_start") private LocalTime day3Start;
    @Column(name = "day_3_end")   private LocalTime day3End;
    @Column(name = "day_4_start") private LocalTime day4Start;
    @Column(name = "day_4_end")   private LocalTime day4End;
    @Column(name = "day_5_start") private LocalTime day5Start;
    @Column(name = "day_5_end")   private LocalTime day5End;
    @Column(name = "day_6_start") private LocalTime day6Start;
    @Column(name = "day_6_end")   private LocalTime day6End;
    @Column(name = "day_7_start") private LocalTime day7Start;
    @Column(name = "day_7_end")   private LocalTime day7End;
}