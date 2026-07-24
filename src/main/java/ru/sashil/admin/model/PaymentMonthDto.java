package ru.sashil.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMonthDto {
    private LocalDate month;
    private Map<Long, Boolean> payments = new HashMap<>();
    private Map<Long, String> statuses = new HashMap<>(); // PENDING, APPROVED, REJECTED
}