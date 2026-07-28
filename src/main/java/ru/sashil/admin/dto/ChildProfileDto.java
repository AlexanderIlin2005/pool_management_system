package ru.sashil.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.sashil.admin.model.SwimmingSkill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChildProfileDto {
    // Данные ребенка
    private Long childId;
    private String firstName;
    private String lastName;
    private String middleName;
    private LocalDate birthDate;
    private Integer age;
    private Integer gradeNumber;
    private String gradeName;
    private SwimmingSkill skill;
    private String parentName;
    private Long parentVkId;
    private String parentEmail;
    private String parentPhone;

    // Группы, в которых состоит ребенок
    private List<GroupInfo> groups;

    // Справки ребенка
    private List<CertificateInfo> certificates;

    // Посещаемость по группам: группа -> (дата -> статус)
    private Map<String, Map<LocalDate, String>> attendance;

    // Оплаты по месяцам
    private Map<LocalDate, PaymentInfo> payments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupInfo {
        private Long groupId;
        private String groupName;
        private Integer groupNumber;
        private String poolName;
        private String trainerName;
        private String schedule;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificateInfo {
        private Long certificateId;
        private LocalDateTime uploadedAt;
        private String status;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private String processedByName;
        private String fileUrl;
        private String comment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private Long paymentId;
        private Boolean isPaid;
        private BigDecimal amount;
        private String status;
        private LocalDateTime paidAt;
        private LocalDateTime verifiedAt;
        private String verifiedByName;
        private String receiptFileUrl;
        private String comment;
    }
}