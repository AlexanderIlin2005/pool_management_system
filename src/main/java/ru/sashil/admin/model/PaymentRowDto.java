package ru.sashil.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRowDto {
    private Long childId;
    private String childName;
    private String groupName;
    private Integer age;
    private String skill;
    private Long parentVkId;
}