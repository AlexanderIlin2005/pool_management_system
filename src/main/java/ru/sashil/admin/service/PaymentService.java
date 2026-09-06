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
    private AuditLogService auditLogService;

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String SETTING_KEY_DEFAULT_AMOUNT = "DEFAULT_PAYMENT_AMOUNT";
    private static final BigDecimal FALLBACK_AMOUNT = new BigDecimal("4000.00");

    /**
     * Получает текущую сумму по умолчанию из настроек (теперь всегда 0).
     */
    public BigDecimal getDefaultAmount() {
        return BigDecimal.ZERO;
    }

    /**
     * Устанавливает сумму по умолчанию (теперь всегда 0, метод оставлен для совместимости).
     */
    @Transactional
    public void setDefaultAmount(BigDecimal amount, AdminUser actor) {
        // Ничего не делаем, сумма всегда 0
        auditLogService.log("DEFAULT_PAYMENT_AMOUNT_IGNORED", actor,
                "Попытка изменить базовую сумму на " + amount + " ₽ (игнорируется, теперь сумма задается вручную)");
    }

    /**
     * Генерирует записи об оплате для всех детей на указанный месяц.
     */
    @Transactional
    public void generatePaymentsForMonth(LocalDate month) {
        LocalDate monthStart = month.withDayOfMonth(1);

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
                payment.setAmount(BigDecimal.ZERO);
                payment.setTotalPaid(BigDecimal.ZERO);
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


    public Map<String, Object> getPaymentTableDataPaged(LocalDate startMonth, LocalDate endMonth,
                                                        String search, int page, int size) {
        long startTime = System.currentTimeMillis();
        System.out.println("🚀 START getPaymentTableDataPaged (page=" + page + ", size=" + size + ")");

        // 1. Формируем список месяцев
        List<LocalDate> months = new ArrayList<>();
        LocalDate current = startMonth;
        while (!current.isAfter(endMonth)) {
            months.add(current);
            current = current.plusMonths(1);
        }

        // Форматируем месяцы для отображения
        List<String> monthLabels = months.stream()
                .map(m -> m.format(DateTimeFormatter.ofPattern("MMM yyyy")))
                .toList();

        // 2. Строим условия поиска
        String searchCondition = "";
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            searchCondition = " AND (c.first_name ILIKE ? OR c.last_name ILIKE ?) ";
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }

        // 3. Получаем ОБЩЕЕ КОЛИЧЕСТВО детей
        String countSql = """
        SELECT COUNT(DISTINCT c.id) as total
        FROM pool.children c
        WHERE EXISTS (SELECT 1 FROM pool.group_children gc WHERE gc.child_id = c.id)
        %s
    """.formatted(searchCondition);

        List<Object> countParams = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            countParams.add("%" + search + "%");
            countParams.add("%" + search + "%");
        }

        int totalItems = jdbcTemplate.queryForObject(countSql, countParams.toArray(), Integer.class);
        int totalPages = (int) Math.ceil((double) totalItems / size);

        System.out.println("📊 Total children: " + totalItems + ", pages: " + totalPages);

        // 4. Получаем ТОЛЬКО нужных детей с пагинацией
        String childrenSql = """
        SELECT 
            c.id as child_id,
            c.first_name,
            c.last_name,
            c.age,
            c.skill::text as skill,
            p.vk_id as parent_vk_id
        FROM pool.children c
        JOIN pool.parents p ON c.parent_id = p.id
        WHERE EXISTS (SELECT 1 FROM pool.group_children gc WHERE gc.child_id = c.id)
        %s
        ORDER BY c.last_name, c.first_name
        LIMIT ? OFFSET ?
    """.formatted(searchCondition);

        int offset = (page - 1) * size;
        List<Object> childrenParams = new ArrayList<>(params);
        childrenParams.add(size);
        childrenParams.add(offset);

        List<Map<String, Object>> pagedChildren = jdbcTemplate.queryForList(childrenSql, childrenParams.toArray());

        if (pagedChildren.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("months", monthLabels);
            result.put("rows", new ArrayList<>());
            result.put("currentPage", page);
            result.put("totalPages", totalPages);
            result.put("totalItems", totalItems);
            result.put("pageSize", size);
            return result;
        }

        // 5. Получаем ID детей на текущей странице
        List<Long> childIds = pagedChildren.stream()
                .map(row -> (Long) row.get("child_id"))
                .toList();

        // 6. Получаем платежи ТОЛЬКО для этих детей
        String placeholders = String.join(",", Collections.nCopies(childIds.size(), "?"));
        String paymentsSql = """
        SELECT 
            pm.id,
            pm.child_id,
            pm.month_year,
            pm.is_paid,
            pm.status,
            pm.amount,
            pm.total_paid,
            pm.receipt_file_url
        FROM pool.payments pm
        WHERE pm.child_id IN (%s)
        AND pm.month_year BETWEEN ? AND ?
    """.formatted(placeholders);

        List<Object> paymentParams = new ArrayList<>(childIds);
        paymentParams.add(java.sql.Date.valueOf(startMonth));
        paymentParams.add(java.sql.Date.valueOf(endMonth));

        List<Map<String, Object>> payments = jdbcTemplate.queryForList(paymentsSql, paymentParams.toArray());

        System.out.println("⏱ SQL: children=" + totalItems +
                ", paged=" + pagedChildren.size() +
                ", payments=" + payments.size());

        // 7. Группируем данные в ПЛОСКУЮ структуру для Thymeleaf
        // Создаем карту: childId -> массив значений для каждого месяца
        Map<Long, String[]> monthStatusMap = new HashMap<>();
        Map<Long, String[]> monthAmountMap = new HashMap<>();
        Map<Long, String[]> monthTotalMap = new HashMap<>();
        Map<Long, BigDecimal> totalPaidMap = new HashMap<>();
        Map<Long, String> childNameMap = new LinkedHashMap<>();
        Map<Long, Integer> childAgeMap = new HashMap<>();
        Map<Long, String> childSkillMap = new HashMap<>();
        Map<Long, Long> childParentVkMap = new HashMap<>();

        // Инициализация для каждого ребенка
        for (Map<String, Object> childRow : pagedChildren) {
            Long childId = (Long) childRow.get("child_id");
            childNameMap.put(childId, childRow.get("last_name") + " " + childRow.get("first_name"));
            childAgeMap.put(childId, (Integer) childRow.get("age"));
            childSkillMap.put(childId, (String) childRow.get("skill"));
            childParentVkMap.put(childId, (Long) childRow.get("parent_vk_id"));
            totalPaidMap.put(childId, BigDecimal.ZERO);

            // Инициализируем массивы для месяцев
            String[] statusArray = new String[months.size()];
            String[] amountArray = new String[months.size()];
            String[] totalArray = new String[months.size()];
            Arrays.fill(statusArray, "NOT_GENERATED");
            Arrays.fill(amountArray, "0");
            Arrays.fill(totalArray, "0");

            monthStatusMap.put(childId, statusArray);
            monthAmountMap.put(childId, amountArray);
            monthTotalMap.put(childId, totalArray);
        }

        // Заполняем данные платежей
        for (Map<String, Object> payment : payments) {
            Long childId = (Long) payment.get("child_id");
            if (!monthStatusMap.containsKey(childId)) continue;

            java.sql.Date sqlDate = (java.sql.Date) payment.get("month_year");
            LocalDate paymentMonth = sqlDate.toLocalDate();

            // Находим индекс месяца
            int monthIndex = months.indexOf(paymentMonth);
            if (monthIndex == -1) continue;

            String status = (String) payment.get("status");
            BigDecimal totalPaid = (BigDecimal) payment.get("total_paid");
            BigDecimal amount = (BigDecimal) payment.get("amount");

            // Заполняем статус
            String displayStatus;
            if ("PAID".equals(status)) {
                displayStatus = "paid";
            } else if ("REJECTED".equals(status)) {
                displayStatus = "rejected";
            } else if ("PENDING".equals(status)) {
                displayStatus = "pending";
            } else {
                displayStatus = "not_generated";
            }
            monthStatusMap.get(childId)[monthIndex] = displayStatus;

            // Заполняем суммы
            monthAmountMap.get(childId)[monthIndex] = amount != null ? amount.toString() : "0";
            monthTotalMap.get(childId)[monthIndex] = totalPaid != null ? totalPaid.toString() : "0";

            // Суммируем общую оплату
            if (totalPaid != null && totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentTotal = totalPaidMap.get(childId);
                totalPaidMap.put(childId, currentTotal.add(totalPaid));
            }
        }

        // 8. Строим финальный результат - ПЛОСКИЙ список строк
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long childId : childNameMap.keySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("childId", childId);
            row.put("childName", childNameMap.get(childId));
            row.put("age", childAgeMap.get(childId));
            row.put("skill", childSkillMap.get(childId));
            row.put("parentVkId", childParentVkMap.get(childId));
            row.put("totalPaid", totalPaidMap.get(childId).toString());

            // Готовые строки для каждого месяца
            row.put("statuses", monthStatusMap.get(childId));    // массив статусов
            row.put("amounts", monthAmountMap.get(childId));     // массив сумм
            row.put("totals", monthTotalMap.get(childId));       // массив total_paid

            rows.add(row);
        }

        // 9. Формируем результат
        Map<String, Object> result = new HashMap<>();
        result.put("months", monthLabels);  // Только названия месяцев
        result.put("rows", rows);
        result.put("currentPage", page);
        result.put("totalPages", totalPages);
        result.put("totalItems", totalItems);
        result.put("pageSize", size);

        System.out.println("⏱ TOTAL execution time: " + (System.currentTimeMillis() - startTime) + " ms");

        return result;
    }



    private List<PaymentRowDto> getChildrenForPayments(String search) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT c.id, c.first_name, c.last_name, c.age, c.skill::text, p.vk_id as parent_vk_id " +
                        "FROM pool.children c " +
                        "JOIN pool.parents p ON c.parent_id = p.id " +
                        "WHERE EXISTS (SELECT 1 FROM pool.group_children gc WHERE gc.child_id = c.id) " +
                        "ORDER BY c.last_name, c.first_name"
        );

        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
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

        // Если сумма не указана - устанавливаем 0
        if (payment.getAmount() == null) {
            payment.setAmount(BigDecimal.ZERO);
        }

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
            payment.setAmount(BigDecimal.ZERO);
            payment.setTotalPaid(BigDecimal.ZERO);
        }

        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String objectName = "receipts/" + UUID.randomUUID().toString() + extension;

        try {
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

        if (LocalDate.now().isAfter(lastWeekStart) && LocalDate.now().isBefore(nextMonth)) {
            List<Child> children = childRepository.findAll();

            for (Child child : children) {
                Optional<Payment> paymentOpt = paymentRepository.findByChildIdAndMonthYear(child.getId(), nextMonth);

                if (paymentOpt.isEmpty() || !paymentOpt.get().getIsPaid()) {
                    boolean alreadySent = notificationRepository.existsByParentVkIdAndChildIdAndMonthYearAndNotificationType(
                            child.getParent().getVkId(),
                            child.getId(),
                            nextMonth,
                            "REMINDER"
                    );

                    if (!alreadySent) {
                        BigDecimal totalPaid = paymentOpt.map(Payment::getTotalPaid).orElse(BigDecimal.ZERO);
                        String message = "🔔 Напоминание!\n\n" +
                                "Уважаемый родитель!\n" +
                                "Не забудьте оплатить абонемент за " +
                                nextMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")) +
                                " для ребенка " + child.getFirstName() + " " + child.getLastName() +
                                "\nОплачено: " + totalPaid + " ₽";

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
                        "Оплачено: " + payment.getTotalPaid() + " ₽";

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
                "p.status, p.created_at, p.amount, p.total_paid, " +
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
                "p.status, p.created_at, p.amount, p.total_paid, p.verified_at, " +
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
     * Обновляет сумму оплаты для конкретного ребенка и месяца с логированием.
     * Сумма может быть добавлена к уже существующей (частичная оплата).
     */
    @Transactional
    public void updatePaymentAmount(Long childId, LocalDate monthYear, BigDecimal amount, AdminUser actor, String comment) {
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
            payment.setAmount(BigDecimal.ZERO);
            payment.setTotalPaid(BigDecimal.ZERO);
            payment.setAmountHistory(new java.util.ArrayList<>());
        }

        // Добавляем сумму к уже существующей (частичная оплата)
        BigDecimal oldTotalPaid = payment.getTotalPaid() != null ? payment.getTotalPaid() : BigDecimal.ZERO;
        BigDecimal newTotalPaid = oldTotalPaid.add(amount);

        payment.setTotalPaid(newTotalPaid);

        // Если сумма оплаты превышает или равна сумме месяца - считаем оплаченным
        if (payment.getAmount() != null && payment.getAmount().compareTo(BigDecimal.ZERO) > 0 && newTotalPaid.compareTo(payment.getAmount()) >= 0) {
            payment.setIsPaid(true);
            payment.setStatus("PAID");
            payment.setPaidAt(LocalDateTime.now());
        } else if (payment.getAmount() != null && payment.getAmount().compareTo(BigDecimal.ZERO) > 0 && newTotalPaid.compareTo(BigDecimal.ZERO) > 0) {
            payment.setStatus("PARTIAL");
        }

        payment.setVerifiedBy(actor);
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setAmountChangeComment(comment);

        // Добавляем запись в историю
        if (payment.getAmountHistory() == null) {
            payment.setAmountHistory(new java.util.ArrayList<>());
        }

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("date", LocalDateTime.now().toString());
        historyEntry.put("actor", actor.getFullName());
        historyEntry.put("oldTotal", oldTotalPaid);
        historyEntry.put("addedAmount", amount);
        historyEntry.put("newTotal", newTotalPaid);
        historyEntry.put("comment", comment != null ? comment : "");

        payment.getAmountHistory().add(historyEntry);

        paymentRepository.save(payment);
        wsNotificationService.sendUpdateNotification("PAYMENT_AMOUNT_UPDATED");

        String childName = getChildName(childId);
        auditLogService.log("PAYMENT_AMOUNT_UPDATED", actor,
                "Добавлена сумма " + amount + " ₽ к оплате для ребенка \"" + childName + "\" за " +
                        monthYear.format(DateTimeFormatter.ofPattern("MMMM yyyy")) +
                        ". Итого оплачено: " + newTotalPaid + " ₽" +
                        (comment != null ? " Комментарий: " + comment : ""));
    }

    private String getChildName(Long childId) {
        try {
            String sql = "SELECT first_name, last_name FROM pool.children WHERE id = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, childId);
            return row.get("first_name") + " " + row.get("last_name");
        } catch (Exception e) {
            return "ребенок ID=" + childId;
        }
    }

    /**
     * Устанавливает сумму абонемента для конкретного ребенка на месяц.
     * Это сумма, которую нужно полностью оплатить.
     */
    @Transactional
    public void setPaymentAmount(Long childId, LocalDate monthYear, BigDecimal amount, AdminUser actor, String comment) {
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
            payment.setTotalPaid(BigDecimal.ZERO);
        }

        BigDecimal oldAmount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        payment.setAmount(amount);
        payment.setVerifiedBy(actor);
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setAmountChangeComment(comment);

        // Проверяем, не оплачено ли уже больше новой суммы
        BigDecimal totalPaid = payment.getTotalPaid() != null ? payment.getTotalPaid() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) > 0 && totalPaid.compareTo(amount) >= 0) {
            payment.setIsPaid(true);
            payment.setStatus("PAID");
            payment.setPaidAt(LocalDateTime.now());
        } else if (amount.compareTo(BigDecimal.ZERO) > 0 && totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            payment.setStatus("PARTIAL");
        }

        paymentRepository.save(payment);
        wsNotificationService.sendUpdateNotification("PAYMENT_AMOUNT_UPDATED");

        String childName = getChildName(childId);
        auditLogService.log("PAYMENT_AMOUNT_SET", actor,
                "Установлена сумма абонемента для ребенка \"" + childName + "\" за " +
                        monthYear.format(DateTimeFormatter.ofPattern("MMMM yyyy")) +
                        ": " + oldAmount + " ₽ → " + amount + " ₽" +
                        (comment != null ? " Комментарий: " + comment : ""));
    }


    /**
     * Подтверждает оплату с указанием суммы (для квитанций).
     * Добавляет сумму в total_paid для соответствующего ребенка и месяца.
     * После подтверждения квитанция переходит в статус PAID и становится обработанной.
     */
    @Transactional
    public void approvePaymentWithAmount(Long paymentId, BigDecimal amount, Long adminId, String comment) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Оплата не найдена"));

        // Добавляем сумму к уже оплаченной
        BigDecimal oldTotalPaid = payment.getTotalPaid() != null ? payment.getTotalPaid() : BigDecimal.ZERO;
        BigDecimal newTotalPaid = oldTotalPaid.add(amount);

        payment.setTotalPaid(newTotalPaid);

        // Устанавливаем статус PAID (квитанция обработана)
        payment.setIsPaid(true);
        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());

        payment.setVerifiedBy(new AdminUser() {{ setId(adminId); }});
        payment.setVerifiedAt(LocalDateTime.now());

        if (comment != null && !comment.isEmpty()) {
            payment.setComment(comment);
        }

        // Добавляем запись в историю
        if (payment.getAmountHistory() == null) {
            payment.setAmountHistory(new java.util.ArrayList<>());
        }

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("date", LocalDateTime.now().toString());
        historyEntry.put("actor", "Бухгалтер (ID: " + adminId + ")");
        historyEntry.put("oldTotal", oldTotalPaid);
        historyEntry.put("addedAmount", amount);
        historyEntry.put("newTotal", newTotalPaid);
        historyEntry.put("comment", comment != null ? comment : "Подтверждение квитанции");

        payment.getAmountHistory().add(historyEntry);

        paymentRepository.save(payment);
        wsNotificationService.sendUpdateNotification("PAYMENT_APPROVED_WITH_AMOUNT");

        // Отправляем уведомление родителю об успешном подтверждении
        try {
            // Форматируем месяц в формате "MM.yyyy" (например, 09.2026)
            String monthFormatted = payment.getMonthYear().format(DateTimeFormatter.ofPattern("MM.yyyy"));

            String notificationMessage = "✅ Ваша оплата за " + monthFormatted +
                    " подтверждена!\nСумма: " + amount + " ₽";

            String sql = "INSERT INTO pool.payment_notifications " +
                    "(parent_vk_id, child_id, month_year, message_text, notification_type, created_at, is_sent) " +
                    "VALUES (?, ?, ?, ?, 'RECEIPT_CONFIRMED', CURRENT_TIMESTAMP, FALSE)";

            jdbcTemplate.update(sql,
                    payment.getChild().getParent().getVkId(),
                    payment.getChild().getId(),
                    payment.getMonthYear(),
                    notificationMessage);
        } catch (Exception e) {
            // Логируем ошибку, но не прерываем выполнение
            e.printStackTrace();
        }
    }

    /**
     * Массовое обновление суммы оплаты для всех детей на указанный месяц.
     */
    @Transactional
    public void updatePaymentAmountForMonth(AdminUser actor, LocalDate monthYear, BigDecimal amount) {
        // Обновляем все записи за указанный месяц
        List<Payment> payments = paymentRepository.findUnpaidForMonth(monthYear);

        if (payments.isEmpty()) {
            throw new IllegalArgumentException("Нет записей об оплате за " +
                    monthYear.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        }

        int updatedCount = 0;
        for (Payment payment : payments) {
            BigDecimal oldAmount = payment.getAmount();
            payment.setAmount(amount);
            payment.setVerifiedBy(actor);
            payment.setVerifiedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            updatedCount++;
        }

        // Логируем действие
        auditLogService.log("PAYMENT_AMOUNT_BULK_UPDATED", actor,
                "Обновлена сумма оплаты для " + updatedCount + " детей за " +
                        monthYear.format(DateTimeFormatter.ofPattern("MMMM yyyy")) +
                        " на " + amount + " ₽");

        wsNotificationService.sendUpdateNotification("PAYMENT_AMOUNT_UPDATED_FOR_MONTH");
    }
}