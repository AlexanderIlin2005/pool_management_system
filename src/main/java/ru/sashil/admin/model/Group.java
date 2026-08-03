package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalTime;
import java.time.LocalDateTime;

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

    @ManyToOne
    @JoinColumn(name = "trainer_id")
    private AdminUser trainer;

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


    @CreationTimestamp
    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;


    // --- НОВЫЕ ПОЛЯ ДЛЯ КРИТЕРИЕВ ВСТУПЛЕНИЯ ---

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "skill_1")
    private String skill1;

    @Column(name = "skill_2")
    private String skill2;


    // Тип абонемента
    @Column(name = "subscription_type")
    private String subscriptionType;

    public String getSubscriptionTypeDisplay() {
        if (subscriptionType == null) return null;
        return switch (subscriptionType) {
            case "ONCE_PER_WEEK" -> "1 раз в неделю";
            case "TWICE_PER_WEEK" -> "2 раза в неделю";
            case "INDIVIDUAL" -> "Индивидуальные занятия с тренером";
            case "FAMILY" -> "Семейное плавание";
            case "AQUA_AEROBICS" -> "Аквааэробика";
            default -> subscriptionType;
        };
    }

}