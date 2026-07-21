package ru.sashil.admin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChildSimple {
    private Long id;
    private String firstName;
    private String lastName;
    private String middleName;
}