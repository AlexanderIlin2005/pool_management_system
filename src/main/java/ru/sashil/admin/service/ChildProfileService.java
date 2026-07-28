package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.dto.ChildProfileDto;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.repository.ChildRepository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChildProfileService {

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public ChildProfileDto getChildProfile(Long childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new RuntimeException("Ребенок не найден"));

        ChildProfileDto profile = new ChildProfileDto();

        // 1. Данные ребенка
        profile.setChildId(child.getId());
        profile.setFirstName(child.getFirstName());
        profile.setLastName(child.getLastName());
        profile.setMiddleName(child.getMiddleName());
        profile.setBirthDate(child.getBirthDate());
        profile.setAge(child.getAge());
        profile.setGradeNumber(child.getGradeNumber());
        profile.setGradeName(child.getGradeName());
        profile.setSkill(child.getSkill());

        // Данные родителя
        if (child.getParent() != null) {
            profile.setParentName(child.getParent().getLastName() + " " + child.getParent().getFirstName());
            profile.setParentVkId(child.getParent().getVkId());
            profile.setParentEmail(child.getParent().getEmail());
        }

        // 2. Группы, в которых состоит ребенок
        profile.setGroups(getChildGroups(childId));

        // 3. Справки ребенка
        profile.setCertificates(getChildCertificates(childId));

        // 4. Посещаемость по группам
        profile.setAttendance(getChildAttendance(childId));

        // 5. Оплаты по месяцам
        profile.setPayments(getChildPayments(childId));

        return profile;
    }

    private List<ChildProfileDto.GroupInfo> getChildGroups(Long childId) {
        String sql = "SELECT g.id, g.name, g.number, p.name as pool_name, " +
                "au.full_name as trainer_name, " +
                "g.day_1_start, g.day_1_end, g.day_2_start, g.day_2_end, " +
                "g.day_3_start, g.day_3_end, g.day_4_start, g.day_4_end, " +
                "g.day_5_start, g.day_5_end, g.day_6_start, g.day_6_end, " +
                "g.day_7_start, g.day_7_end " +
                "FROM pool.group_children gc " +
                "JOIN pool.groups g ON gc.group_id = g.id " +
                "LEFT JOIN pool.pools p ON g.pool_id = p.id " +
                "LEFT JOIN pool.admin_users au ON g.trainer_id = au.id " +
                "WHERE gc.child_id = ? " +
                "ORDER BY g.number";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, childId);
        List<ChildProfileDto.GroupInfo> groups = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            ChildProfileDto.GroupInfo info = new ChildProfileDto.GroupInfo();
            info.setGroupId((Long) row.get("id"));
            info.setGroupName((String) row.get("name"));
            info.setGroupNumber((Integer) row.get("number"));
            info.setPoolName(row.get("pool_name") != null ? (String) row.get("pool_name") : "Не указан");
            info.setTrainerName(row.get("trainer_name") != null ? (String) row.get("trainer_name") : "Не назначен");

            // Формируем расписание
            StringBuilder schedule = new StringBuilder();
            appendDaySchedule(schedule, "Пн", row, 1);
            appendDaySchedule(schedule, "Вт", row, 2);
            appendDaySchedule(schedule, "Ср", row, 3);
            appendDaySchedule(schedule, "Чт", row, 4);
            appendDaySchedule(schedule, "Пт", row, 5);
            appendDaySchedule(schedule, "Сб", row, 6);
            appendDaySchedule(schedule, "Вс", row, 7);
            info.setSchedule(schedule.toString());

            groups.add(info);
        }

        return groups;
    }

    private void appendDaySchedule(StringBuilder sb, String dayName, Map<String, Object> row, int day) {
        Object startObj = row.get("day_" + day + "_start");
        Object endObj = row.get("day_" + day + "_end");

        if (startObj != null && endObj != null) {
            String start = startObj.toString();
            String end = endObj.toString();

            // Обрезаем до формата HH:mm (если есть секунды)
            if (start.length() > 5) start = start.substring(0, 5);
            if (end.length() > 5) end = end.substring(0, 5);

            sb.append(dayName).append(" ").append(start).append("-").append(end).append(" ");
        }
    }

    private List<ChildProfileDto.CertificateInfo> getChildCertificates(Long childId) {
        String sql = "SELECT c.id, c.uploaded_at, c.status, c.date_from, c.date_to, " +
                "c.file_url, c.comment, au.full_name as processed_by_name " +
                "FROM pool.certificates c " +
                "LEFT JOIN pool.admin_users au ON c.processed_by = au.id " +
                "WHERE c.child_id = ? " +
                "ORDER BY c.uploaded_at DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, childId);
        List<ChildProfileDto.CertificateInfo> certificates = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            ChildProfileDto.CertificateInfo info = new ChildProfileDto.CertificateInfo();
            info.setCertificateId((Long) row.get("id"));

            Object uploadedAt = row.get("uploaded_at");
            if (uploadedAt instanceof Timestamp) {
                info.setUploadedAt(((Timestamp) uploadedAt).toLocalDateTime());
            } else if (uploadedAt instanceof LocalDateTime) {
                info.setUploadedAt((LocalDateTime) uploadedAt);
            }

            info.setStatus((String) row.get("status"));
            info.setDateFrom(row.get("date_from") != null ? ((java.sql.Date) row.get("date_from")).toLocalDate() : null);
            info.setDateTo(row.get("date_to") != null ? ((java.sql.Date) row.get("date_to")).toLocalDate() : null);
            info.setFileUrl((String) row.get("file_url"));
            info.setComment((String) row.get("comment"));
            info.setProcessedByName(row.get("processed_by_name") != null ? (String) row.get("processed_by_name") : "-");
            certificates.add(info);
        }

        return certificates;
    }

    private Map<String, Map<LocalDate, String>> getChildAttendance(Long childId) {
        // Получаем последние 3 месяца
        LocalDate now = LocalDate.now();
        LocalDate threeMonthsAgo = now.minusMonths(3);

        String sql = "SELECT pl.lesson_date, g.name as group_name, g.id as group_id, a.status " +
                "FROM pool.attendance a " +
                "JOIN pool.pool_lessons pl ON a.lesson_id = pl.id " +
                "JOIN pool.groups g ON pl.group_id = g.id " +
                "WHERE a.child_id = ? AND pl.lesson_date >= ? " +
                "ORDER BY g.name, pl.lesson_date DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, childId, java.sql.Date.valueOf(threeMonthsAgo));

        // Структура: группа -> дата -> статус
        Map<String, Map<LocalDate, String>> attendanceByGroup = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String groupName = (String) row.get("group_name");
            LocalDate date = ((java.sql.Date) row.get("lesson_date")).toLocalDate();
            String status = (String) row.get("status");

            attendanceByGroup.computeIfAbsent(groupName, k -> new LinkedHashMap<>())
                    .put(date, status != null ? status : "Нет данных");
        }

        return attendanceByGroup;
    }

    private Map<LocalDate, ChildProfileDto.PaymentInfo> getChildPayments(Long childId) {
        String sql = "SELECT p.id, p.month_year, p.is_paid, p.amount, p.status, " +
                "p.paid_at, p.verified_at, p.receipt_file_url, p.comment, " +
                "au.full_name as verified_by_name " +
                "FROM pool.payments p " +
                "LEFT JOIN pool.admin_users au ON p.verified_by = au.id " +
                "WHERE p.child_id = ? " +
                "ORDER BY p.month_year DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, childId);
        Map<LocalDate, ChildProfileDto.PaymentInfo> payments = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            ChildProfileDto.PaymentInfo info = new ChildProfileDto.PaymentInfo();
            info.setPaymentId((Long) row.get("id"));
            info.setIsPaid((Boolean) row.get("is_paid"));
            info.setAmount((java.math.BigDecimal) row.get("amount"));
            info.setStatus((String) row.get("status"));

            Object paidAt = row.get("paid_at");
            if (paidAt instanceof Timestamp) {
                info.setPaidAt(((Timestamp) paidAt).toLocalDateTime());
            } else if (paidAt instanceof LocalDateTime) {
                info.setPaidAt((LocalDateTime) paidAt);
            }

            Object verifiedAt = row.get("verified_at");
            if (verifiedAt instanceof Timestamp) {
                info.setVerifiedAt(((Timestamp) verifiedAt).toLocalDateTime());
            } else if (verifiedAt instanceof LocalDateTime) {
                info.setVerifiedAt((LocalDateTime) verifiedAt);
            }

            info.setReceiptFileUrl((String) row.get("receipt_file_url"));
            info.setComment((String) row.get("comment"));
            info.setVerifiedByName(row.get("verified_by_name") != null ? (String) row.get("verified_by_name") : "-");

            LocalDate monthYear = ((java.sql.Date) row.get("month_year")).toLocalDate();
            payments.put(monthYear, info);
        }

        return payments;
    }
}