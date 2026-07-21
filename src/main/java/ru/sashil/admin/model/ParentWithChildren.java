package ru.sashil.admin.model;

import lombok.Data;

@Data
public class ParentWithChildren {
    private Long id;
    private String fullName;
    private String lastName;
    private String firstName;
    private String middleName;
    private String email;
    private String phone;
    private String child1;
    private Long child1Id; // Добавлено
    private String child2;
    private Long child2Id; // Добавлено
    private String child3;
    private Long child3Id; // Добавлено
}