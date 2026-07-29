package ru.sashil.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChildEditDto {
    private Long childId;
    private String firstName;
    private String lastName;
    private String middleName;
    private LocalDate birthDate;
    private Integer age;
    private Integer gradeNumber;
    private String gradeName;
    private String skill;

    // Данные родителя
    private Long parentId;
    private String parentFirstName;
    private String parentLastName;
    private String parentEmail;
    private String parentPhone;
}