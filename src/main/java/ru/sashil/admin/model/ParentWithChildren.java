package ru.sashil.admin.model;

import lombok.Data;

@Data
public class ParentWithChildren {
    private Long id;
    private String fullName;
    private String lastName;   // Новое поле
    private String firstName;  // Новое поле
    private String middleName; // Новое поле
    private String email;
    private String phone;
    private String child1;
    private String child2;
    private String child3;
}