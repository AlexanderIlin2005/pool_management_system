package ru.sashil.admin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor // <-- Добавь эту аннотацию
public class GroupChildId implements Serializable {
    private Long groupId;
    private Long childId;
}