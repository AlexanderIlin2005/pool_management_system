package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "children", schema = "pool")
@Data
public class Child {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "age")
    private Integer age;

    @Column(name = "grade_number")
    private Integer gradeNumber;

    @Column(name = "grade_name")
    private String gradeName;


    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false)
    @Convert(converter = SwimmingSkill.SwimmingSkillConverter.class)
    private SwimmingSkill skill;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}