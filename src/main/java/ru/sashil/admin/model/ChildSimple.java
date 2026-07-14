package ru.sashil.admin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Упрощенная модель ребенка только для списков (без Enum полей, чтобы избежать ошибок маппинга)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChildSimple {
    private Long id;
    private String firstName;
    private String lastName;
}