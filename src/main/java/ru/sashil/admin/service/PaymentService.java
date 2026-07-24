package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.sashil.admin.dto.PaymentRowDto;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.*;
import ru.sashil.common.service.MinIOService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentNotificationRepository notificationRepository;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupChildRepository groupChildRepository;

    @Autowired
    private MinIOService minioService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("4000.00");

    /**
     * Генерирует записи об оплате для всех детей на указанный месяц.
     */
    @Transactional
    public void generatePaymentsForMonth(LocalDate month) {
        LocalDate monthStart = month.withDayOfMonth(1);

        // Получаем всех детей, у которых есть группа
        String sql = "SELECT DISTINCT c.id FROM pool.children c " +
                "JOIN pool.group_children gc ON c.id = gc.child_id";

        List<Long> childIds = jdbcTemplate.queryForList(sql, Long.class);

        for (Long childId : childIds) {
            Optional<Payment> existing = paymentRepository.findByChildIdAndMonthYear(childId, monthStart);
            if (existing.isEmpty()) {
                Payment payment = new Payment();
                Child child = new Child();
                child.setId(childId);
                payment.setChild(child);
                payment.setMonthYear(monthStart);
                payment.setIsPaid(false);
                payment.setStatus("PENDING");
                payment.setAmount(DEFAULT_AMOUNT);
                paymentRepository.save(payment);
            }
        }
    }

    /**
     * Генерирует оплаты на следующие 12 месяцев для всех детей.
     */
    @Transactional
    public void generatePaymentsForNextYear() {
        LocalDate today = LocalDate.now();
        LocalDate startMonth = today.withDayOfMonth(1);

        for (int i = 0; i < 12; i++) {
            LocalDate month = startMonth.plusMonths(i);
            generatePaymentsForMonth(month);
        }
    }

    public Map<String, Object> getPaymentTableData(LocalDate startMonth, LocalDate endMonth, String search) {
        // Получаем список месяцев
        List<LocalDate> months = new ArrayList<>();
        LocalDate current = startMonth;
        while (!current.isAfter(endMonth)) {
            months.add(current);
            current = current.plusMonths(1);
        }

        // Получаем список детей с фильтрацией (без группы)
        List<PaymentRowDto> children = getChildrenForPayments(search);

        // Получаем все оплаты за период
        List<Payment> payments = paymentRepository.findPaymentsInPeriod(startMonth, endMonth);

        // Строим карту оплат: childId -> month -> Payment
        Map<Long, Map<LocalDate, Payment>> paymentMap = new HashMap<>();
        for (Payment p : payments) {
            paymentMap.computeIfAbsent(p.getChild().getId(), k -> new HashMap<>())
                    .put(p.getMonthYear(), p);
        }

        // Строим данные для таблицы
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PaymentRowDto child : children) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("childId", child.getChildId());
            row.put("childName", child.getChildName());
            row.put("age", child.getAge());
            row.put("skill", child.getSkill());
            row.put("parentVkId", child.getParentVkId());

            Map<LocalDate, Map<String, Object>> monthData = new LinkedHashMap<>();
            for (LocalDate month : months) {
                Map<String, Object> cell = new HashMap<>();
                Payment payment = paymentMap.getOrDefault(child.getChildId(), Collections.emptyMap()).get(month);

                if (payment != null) {
                    cell.put("id", payment.getId());
                    cell.put("isPaid", payment.getIsPaid());
                    cell.put("status", payment.getStatus());
                    cell.put("receiptFileUrl", payment.getReceiptFileUrl());
                    cell.put("amount", payment.getAmount());  // <-- ДОБАВЛЯЕМ amount
                    cell.put("month", month);
                } else {
                    cell.put("isPaid", false);
                    cell.put("status", "NOT_GENERATED");
                    cell.put("amount", DEFAULT_AMOUNT);  // <-- ДОБАВЛЯЕМ amount
                }
                monthData.put(month, cell);
            }
            row.put("months", monthData);
            rows.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("months", months);
        result.put("rows", rows);
        result.put("startMonth", startMonth);
        result.put("endMonth", endMonth);

        return result;
    }

    private List<PaymentRowDto> getChildrenForPayments(String search) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT c.id, c.first_name, c.last_name, c.age, c.skill::text, p.vk_id as parent_vk_id " +
                        "FROM pool.children c " +
                        "JOIN pool.parents p ON c.parent_id = p.id " +
                        "WHERE EXISTS (SELECT 1 FROM pool.group_children gc WHERE gc.child_id = c.id) " +  // <-- ТОЛЬКО ДЕТИ В ГРУППАХ
                        "ORDER BY c.last_name, c.first_name"
        );

        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            // Вставляем поиск в подзапрос
            sql = new StringBuilder(
                    "SELECT DISTINCT c.id, c.first_name, c.last_name, c.age, c.skill::text, p.vk_id as parent_vk_id " +
                            "FROM pool.children c " +
                            "JOIN pool.parents p ON c.parent_id = p.id " +
                            "WHERE EXISTS (SELECT 1 FROM pool.group_children gc WHERE gc.child_id = c.id) " +
                            "AND (c.first_name ILIKE ? OR c.last_name ILIKE ?) " +
                            "ORDER BY c.last_name, c.first_name"
            );
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
        }

        List<PaymentRowDto> result = new ArrayList<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        for (Map<String, Object> row : rows) {
            PaymentRowDto dto = new PaymentRowDto();
            dto.setChildId((Long) row.get("id"));
            dto.setChildName(row.get("last_name") + " " + row.get("first_name"));
            dto.setAge((Integer) row.get("age"));
            dto.setSkill((String) row.get("skill"));
            dto.setParentVkId((Long) row.get("parent_vk_id"));
            result.add(dto);
        }

        return result;
    }

    /**
     * Подтверждает оплату (для бухгалтера).
     */
    @Transactional
    public void approvePayment(Long paymentId, Long adminId, String comment) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Оплата не найдена"));

        payment.setIsPaid(true);
        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        payment.setVerifiedBy(new AdminUser() {{ setId(adminId); }});
        payment.setVerifiedAt(LocalDateTime.now());

        if (comment != null) {
            payment.setComment(comment);
        }

        paymentRepository.save(payment);
        wsNotificationService.sendUpdateNotification("PAYMENT_APPROVED");
    }

    /**
     * Отклоняет оплату (для бухгалтера).
     */
    @Transactional
    public void rejectPayment(Long paymentId, Long adminId, String comment) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Оплата не найдена"));

        payment.setIsPaid(false);
        payment.setStatus("REJECTED");
        payment.setVerifiedBy(new AdminUser() {{ setId(adminId); }});
        payment.setVerifiedAt(LocalDateTime.now());

        if (comment != null) {
            payment.setComment(comment);
        }

        paymentRepository.save(payment);
        wsNotificationService.sendUpdateNotification("PAYMENT_REJECTED");
    }

    /**
     * Загружает квитанцию об оплате (через бота).
     * ИСПРАВЛЕНО: обрабатываем Exception от MinIO
     */
    /**
     * Загружает квитанцию об оплате (через бота).
     */
    @Transactional
    public void uploadReceipt(Long childId, LocalDate monthYear, MultipartFile file, Long parentVkId) throws IOException {
        Optional<Payment> paymentOpt = paymentRepository.findByChildIdAndMonthYear(childId, monthYear);
        Payment payment;

        if (paymentOpt.isPresent()) {
            payment = paymentOpt.get();
        } else {
            payment = new Payment();
            Child child = new Child();
            child.setId(childId);
            payment.setChild(child);
            payment.setMonthYear(monthYear);
            payment.setIsPaid(false);
            payment.setStatus("PENDING");
            payment.setAmount(DEFAULT_AMOUNT);
        }

        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String objectName = "receipts/" + UUID.randomUUID().toString() + extension;

        try {
            // Передаем оригинальное имя для определения Content-Type
            String fileUrl = minioService.uploadFileToDocsBucket(file.getInputStream(), objectName, file.getSize(), originalFileName);
            payment.setReceiptFileUrl(fileUrl);
            payment.setReceiptOriginalName(originalFileName);
            payment.setStatus("PENDING");
            paymentRepository.save(payment);
            wsNotificationService.sendUpdateNotification("RECEIPT_UPLOADED");
        } catch (Exception e) {
            throw new IOException("Ошибка загрузки файла в MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Отправляет напоминания об оплате за следующий месяц.
     */
    @Transactional
    public void sendPaymentReminders() {
        LocalDate nextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        LocalDate lastWeekStart = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(7);

        // Если сейчас последняя неделя месяца
        if (LocalDate.now().isAfter(lastWeekStart) && LocalDate.now().isBefore(nextMonth)) {
            // Получаем всех детей
            List<Child> children = childRepository.findAll();

            for (Child child : children) {
                // Проверяем, есть ли оплата за следующий месяц
                Optional<Payment> paymentOpt = paymentRepository.findByChildIdAndMonthYear(child.getId(), nextMonth);

                // Если оплаты нет или она не оплачена
                if (paymentOpt.isEmpty() || !paymentOpt.get().getIsPaid()) {
                    // Проверяем, не отправляли ли уже напоминание в этом месяце
                    boolean alreadySent = notificationRepository.existsByParentVkIdAndChildIdAndMonthYearAndNotificationType(
                            child.getParent().getVkId(),
                            child.getId(),
                            nextMonth,
                            "REMINDER"
                    );

                    if (!alreadySent) {
                        String message = "🔔 Напоминание!\n\n" +
                                "Уважаемый родитель!\n" +
                                "Не забудьте оплатить абонемент за " +
                                nextMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")) +
                                " для ребенка " + child.getFirstName() + " " + child.getLastName();

                        PaymentNotification notification = new PaymentNotification();
                        notification.setParentVkId(child.getParent().getVkId());
                        notification.setChild(child);
                        notification.setMonthYear(nextMonth);
                        notification.setMessageText(message);
                        notification.setNotificationType("REMINDER");
                        notification.setCreatedAt(LocalDateTime.now());
                        notificationRepository.save(notification);
                    }
                }
            }
        }
    }

    /**
     * Отправляет уведомление о просроченных оплатах.
     */
    @Transactional
    public void sendOverdueNotifications() {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        List<Payment> overdue = paymentRepository.findOverduePayments(currentMonth);

        for (Payment payment : overdue) {
            // Проверяем, не отправляли ли уже уведомление
            boolean alreadySent = notificationRepository.existsByParentVkIdAndChildIdAndMonthYearAndNotificationType(
                    payment.getChild().getParent().getVkId(),
                    payment.getChild().getId(),
                    payment.getMonthYear(),
                    "OVERDUE"
            );

            if (!alreadySent) {
                String message = "⚠️ ВНИМАНИЕ!\n\n" +
                        "Уважаемый родитель!\n" +
                        "Абонемент за " +
                        payment.getMonthYear().format(DateTimeFormatter.ofPattern("MMMM yyyy")) +
                        " для ребенка " + payment.getChild().getFirstName() + " " + payment.getChild().getLastName() +
                        " до сих пор не оплачен!\n\n" +
                        "Пожалуйста, произведите оплату как можно скорее.\n" +
                        "Сумма: " + payment.getAmount() + " ₽";

                PaymentNotification notification = new PaymentNotification();
                notification.setParentVkId(payment.getChild().getParent().getVkId());
                notification.setChild(payment.getChild());
                notification.setMonthYear(payment.getMonthYear());
                notification.setMessageText(message);
                notification.setNotificationType("OVERDUE");
                notification.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(notification);
            }
        }
    }

    /**
     * Получает уведомления для отправки ботом.
     */
    public List<PaymentNotification> getPendingNotifications() {
        return notificationRepository.findByIsSentFalseOrderByCreatedAtAsc();
    }

    /**
     * Отмечает уведомление как отправленное.
     */
    @Transactional
    public void markNotificationSent(Long notificationId) {
        PaymentNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Уведомление не найдено"));
        notification.setIsSent(true);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    /**
     * Получает список квитанций со статусом PENDING (ожидают проверки).
     */
    public List<Map<String, Object>> getPendingReceipts() {
        String sql = "SELECT p.id, p.child_id, p.month_year, p.receipt_file_url, p.receipt_original_name, " +
                "p.status, p.created_at, p.amount, " +
                "c.first_name, c.last_name, c.age, " +
                "par.vk_id as parent_vk_id, " +
                "par.first_name as parent_first_name, par.last_name as parent_last_name " +
                "FROM pool.payments p " +
                "JOIN pool.children c ON p.child_id = c.id " +
                "JOIN pool.parents par ON c.parent_id = par.id " +
                "WHERE p.status = 'PENDING' AND p.receipt_file_url IS NOT NULL " +
                "ORDER BY p.created_at DESC";
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Получает список всех обработанных квитанций (архив).
     */
    public List<Map<String, Object>> getProcessedReceipts() {
        String sql = "SELECT p.id, p.child_id, p.month_year, p.receipt_file_url, p.receipt_original_name, " +
                "p.status, p.created_at, p.amount, p.verified_at, " +
                "p.comment, " +
                "c.first_name, c.last_name, c.age, " +
                "par.vk_id as parent_vk_id, " +
                "par.first_name as parent_first_name, par.last_name as parent_last_name, " +
                "au.full_name as verified_by_name " +
                "FROM pool.payments p " +
                "JOIN pool.children c ON p.child_id = c.id " +
                "JOIN pool.parents par ON c.parent_id = par.id " +
                "LEFT JOIN pool.admin_users au ON p.verified_by = au.id " +
                "WHERE p.status IN ('PAID', 'REJECTED') AND p.receipt_file_url IS NOT NULL " +
                "ORDER BY p.verified_at DESC";
        return jdbcTemplate.queryForList(sql);
    }


    /**
     * Обновляет сумму оплаты для конкретного ребенка и месяца.
     */
    @Transactional
    public void updatePaymentAmount(Long childId, LocalDate monthYear, BigDecimal amount) {
        Optional<Payment> paymentOpt = paymentRepository.findByChildIdAndMonthYear(childId, monthYear);
        Payment payment;

        if (paymentOpt.isPresent()) {
            payment = paymentOpt.get();
        } else {
            payment = new Payment();
            Child child = new Child();
            child.setId(childId);
            payment.setChild(child);
            payment.setMonthYear(monthYear);
            payment.setIsPaid(false);
            payment.setStatus("PENDING");
        }

        payment.setAmount(amount);
        paymentRepository.save(payment);
        wsNotificationService.sendUpdateNotification("PAYMENT_AMOUNT_UPDATED");
    }


}