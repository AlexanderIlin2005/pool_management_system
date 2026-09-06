package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.model.PaymentNotification;
import ru.sashil.admin.model.Payment;
import ru.sashil.admin.repository.PaymentNotificationRepository;
import ru.sashil.admin.repository.PaymentRepository;
import ru.sashil.admin.service.GroupService;
import ru.sashil.admin.service.PaymentService;
import ru.sashil.admin.service.WsNotificationService;
import ru.sashil.admin.service.AuditLogService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupService groupService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @Autowired
    private PaymentNotificationRepository paymentNotificationRepository;

    @Autowired
    private AuditLogService auditLogService;

    private static final LocalDate MIN_DATE = LocalDate.of(2026, 9, 1);

    /**
     * Проверяет, имеет ли пользователь доступ к бухгалтерским разделам
     */
    private boolean hasAccountingAccess(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return false;
        return user.getRole() == AdminUser.Role.ACCOUNTANT || user.getRole() == AdminUser.Role.ADMIN;
    }

    /**
     * Проверяет, является ли пользователь бухгалтером или админом (для операций с оплатами)
     */
    private boolean canManagePayments(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return false;
        return user.getRole() == AdminUser.Role.ACCOUNTANT || user.getRole() == AdminUser.Role.ADMIN;
    }

    /**
     * Проверяет, является ли пользователь бухгалтером
     */
    private boolean isAccountant(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return false;
        return user.getRole() == AdminUser.Role.ACCOUNTANT;
    }

    @GetMapping
    public String paymentsPage(Model model, HttpSession session,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) Integer year,
                               @RequestParam(required = false) Integer month,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer size) {

        long startAll = System.currentTimeMillis();
        System.out.println("🚀 ===== START /payments =====");

        if (!hasAccountingAccess(session)) {
            return "redirect:/login";
        }
        long t1 = System.currentTimeMillis();
        System.out.println("⏱ Auth check: " + (t1 - startAll) + " ms");

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        LocalDate startMonth;
        LocalDate endMonth;

        if (year != null && month != null) {
            startMonth = LocalDate.of(year, month, 1);
            endMonth = startMonth.plusMonths(8);
            if (startMonth.isBefore(MIN_DATE)) {
                return "redirect:/payments";
            }
        } else {
            startMonth = MIN_DATE;
            endMonth = startMonth.plusMonths(8);
        }
        long t2 = System.currentTimeMillis();
        System.out.println("⏱ Date calc: " + (t2 - t1) + " ms");

        // ✅ Получаем данные с пагинацией (теперь быстро!)
        Map<String, Object> data = paymentService.getPaymentTableDataPaged(
                startMonth, endMonth, search, page, size
        );
        long t3 = System.currentTimeMillis();
        System.out.println("⏱ Service call: " + (t3 - t2) + " ms");

        boolean canGoPrevYear = startMonth.minusYears(1).isAfter(MIN_DATE) ||
                startMonth.minusYears(1).isEqual(MIN_DATE);

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "payments");
        model.addAttribute("months", data.get("months"));
        model.addAttribute("rows", data.get("rows"));
        model.addAttribute("currentSearch", search);
        model.addAttribute("startMonth", startMonth);
        model.addAttribute("endMonth", endMonth);
        model.addAttribute("prevYearStart", startMonth.minusYears(1));
        model.addAttribute("nextYearStart", startMonth.plusYears(1));
        model.addAttribute("canGoPrevYear", canGoPrevYear);
        model.addAttribute("monthFormatter", DateTimeFormatter.ofPattern("MMM yyyy"));
        model.addAttribute("defaultAmount", BigDecimal.ZERO);
        model.addAttribute("allMonths", getMonthsInPeriod(startMonth, endMonth));
        model.addAttribute("currentPage", data.get("currentPage"));
        model.addAttribute("totalPages", data.get("totalPages"));
        model.addAttribute("totalItems", data.get("totalItems"));
        model.addAttribute("pageSize", size);

        long t4 = System.currentTimeMillis();
        System.out.println("⏱ Model setup: " + (t4 - t3) + " ms");

        long t5 = System.currentTimeMillis();
        String view = "payments";
        System.out.println("⏱ View name: " + (t5 - t4) + " ms");

        long totalTime = System.currentTimeMillis();
        System.out.println("⏱ TOTAL controller time: " + (totalTime - startAll) + " ms");
        System.out.println("🚀 ===== END /payments =====");

        return view;
    }

    /**
     * Получает список всех месяцев в периоде для выбора
     */
    private List<LocalDate> getMonthsInPeriod(LocalDate startMonth, LocalDate endMonth) {
        List<LocalDate> months = new ArrayList<>();
        LocalDate current = startMonth;
        while (!current.isAfter(endMonth)) {
            months.add(current);
            current = current.plusMonths(1);
        }
        return months;
    }

    /**
     * Ручное добавление суммы оплаты для конкретного ребенка и месяца
     * Доступно для ADMIN и ACCOUNTANT
     */
    @PostMapping("/add-amount")
    public String addPaymentAmount(@RequestParam Long childId,
                                   @RequestParam String monthYear,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(required = false) String comment,
                                   HttpSession session) {
        if (!canManagePayments(session)) {
            return "redirect:/login";
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "redirect:/payments?error=amount_positive_required";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        try {
            LocalDate month = LocalDate.parse(monthYear + "-01");
            paymentService.updatePaymentAmount(childId, month, amount, user, comment);
            return "redirect:/payments?success=amount_added";
        } catch (Exception e) {
            return "redirect:/payments?error=" + e.getMessage();
        }
    }

    /**
     * Установка суммы абонемента для конкретного ребенка и месяца
     * Доступно для ADMIN и ACCOUNTANT
     */
    @PostMapping("/set-amount")
    public String setPaymentAmount(@RequestParam Long childId,
                                   @RequestParam String monthYear,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(required = false) String comment,
                                   HttpSession session) {
        if (!canManagePayments(session)) {
            return "redirect:/login";
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "redirect:/payments?error=amount_negative_required";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        try {
            LocalDate month = LocalDate.parse(monthYear + "-01");
            paymentService.setPaymentAmount(childId, month, amount, user, comment);
            return "redirect:/payments?success=amount_set";
        } catch (Exception e) {
            return "redirect:/payments?error=" + e.getMessage();
        }
    }

    @PostMapping("/{id}/approve")
    public String approvePayment(@PathVariable Long id,
                                 @RequestParam(required = false) String comment,
                                 HttpSession session) {
        if (!hasAccountingAccess(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        paymentService.approvePayment(id, user.getId(), comment);
        wsNotificationService.sendUpdateNotification("PAYMENT_APPROVED");
        return "redirect:/payments?success=approved";
    }

    @PostMapping("/{id}/reject")
    public String rejectPayment(@PathVariable Long id,
                                @RequestParam(required = false) String comment,
                                HttpSession session) {
        if (!hasAccountingAccess(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        paymentService.rejectPayment(id, user.getId(), comment);
        wsNotificationService.sendUpdateNotification("PAYMENT_REJECTED");
        return "redirect:/payments?success=rejected";
    }

    @PostMapping("/upload-receipt")
    public String uploadReceipt(@RequestParam Long childId,
                                @RequestParam String monthYear,
                                @RequestParam MultipartFile file,
                                HttpSession session) {
        if (!hasAccountingAccess(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        try {
            LocalDate month = LocalDate.parse(monthYear);
            paymentService.uploadReceipt(childId, month, file, user.getId());
            wsNotificationService.sendUpdateNotification("RECEIPT_UPLOADED");
            return "redirect:/payments?success=receipt_uploaded";
        } catch (Exception e) {
            return "redirect:/payments?error=upload_failed";
        }
    }

    /**
     * Отправка напоминания родителю - доступно только для бухгалтера
     */
    @PostMapping("/send-reminder/{parentVkId}")
    public String sendReminder(@PathVariable Long parentVkId,
                               @RequestParam Long childId,
                               @RequestParam(required = false) String comment,
                               HttpSession session) {
        if (!isAccountant(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        PaymentNotification notification = new PaymentNotification();
        notification.setParentVkId(parentVkId);
        Child child = new Child();
        child.setId(childId);
        notification.setChild(child);
        notification.setMonthYear(LocalDate.now().withDayOfMonth(1));
        notification.setNotificationType("OVERDUE");

        String message = "⚠️ Уважаемый родитель!\n\n" +
                "Администрация напоминает о необходимости оплаты абонемента.\n" +
                "Пожалуйста, произведите оплату как можно скорее.";

        if (comment != null && !comment.isEmpty()) {
            message += "\n\nКомментарий администратора: " + comment;
        }

        notification.setMessageText(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setIsSent(false);

        paymentNotificationRepository.save(notification);
        wsNotificationService.sendUpdateNotification("PAYMENT_REMINDER_SENT");

        return "redirect:/payments?success=reminder_sent";
    }

    /**
     * Массовое обновление суммы абонемента для всех детей на указанный месяц
     * Доступно только для бухгалтера
     */
    @PostMapping("/update-amount-for-month")
    public String updateAmountForMonth(@RequestParam String monthYear,
                                       @RequestParam BigDecimal amount,
                                       @RequestParam String confirmation,
                                       HttpSession session) {
        if (!isAccountant(session)) {
            return "redirect:/login";
        }

        if (!"ПОДТВЕРЖДАЮ".equalsIgnoreCase(confirmation.trim())) {
            return "redirect:/payments?error=confirmation_required";
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "redirect:/payments?error=amount_negative_required";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        try {
            LocalDate month = LocalDate.parse(monthYear + "-01");
            paymentService.updatePaymentAmountForMonth(user, month, amount);
            return "redirect:/payments?success=amount_updated_for_month";
        } catch (Exception e) {
            return "redirect:/payments?error=" + e.getMessage();
        }
    }

    @GetMapping("/settings")
    public String settingsPage(Model model, HttpSession session) {
        if (!isAccountant(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        BigDecimal currentAmount = paymentService.getDefaultAmount();
        model.addAttribute("currentAmount", currentAmount);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "payment-settings");

        return "payment-settings";
    }

    @PostMapping("/update-default-amount")
    public String updateDefaultAmount(@RequestParam BigDecimal amount,
                                      HttpSession session) {
        if (!isAccountant(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        try {
            paymentService.setDefaultAmount(amount, user);
            return "redirect:/payments/settings?success=true";
        } catch (Exception e) {
            return "redirect:/payments/settings?error=" + e.getMessage();
        }
    }

    @GetMapping("/notifications")
    public String notificationsPage(Model model, HttpSession session) {
        if (!isAccountant(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        List<PaymentNotification> pendingNotifications = paymentService.getPendingNotifications();
        model.addAttribute("pendingNotifications", pendingNotifications);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "payment-notifications");

        return "payment-notifications";
    }

    @PostMapping("/notifications/{id}/send")
    public String sendNotification(@PathVariable Long id, HttpSession session) {
        if (!isAccountant(session)) {
            return "redirect:/login";
        }

        try {
            paymentService.markNotificationSent(id);
            return "redirect:/payments/notifications?success=notification_sent";
        } catch (Exception e) {
            return "redirect:/payments/notifications?error=" + e.getMessage();
        }
    }

    /**
     * Изменение суммы оплаты для конкретного ребенка и месяца
     * Может увеличивать или уменьшать уже оплаченную сумму
     * Доступно для ADMIN и ACCOUNTANT
     */
    @PostMapping("/edit-payment")
    public String editPaymentAmount(@RequestParam Long childId,
                                    @RequestParam String monthYear,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam(required = false) String comment,
                                    HttpSession session) {
        if (!canManagePayments(session)) {
            return "redirect:/login";
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "redirect:/payments?error=amount_negative_required";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        try {
            LocalDate month = LocalDate.parse(monthYear + "-01");

            // Находим существующую запись через репозиторий
            Optional<Payment> paymentOpt = paymentRepository.findByChildIdAndMonthYear(childId, month);

            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();
                BigDecimal oldTotalPaid = payment.getTotalPaid() != null ? payment.getTotalPaid() : BigDecimal.ZERO;

                // Обновляем сумму
                payment.setTotalPaid(amount);

                // Обновляем статус
                BigDecimal monthAmount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
                if (amount.compareTo(BigDecimal.ZERO) > 0 && monthAmount.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(monthAmount) >= 0) {
                    payment.setIsPaid(true);
                    payment.setStatus("PAID");
                    payment.setPaidAt(LocalDateTime.now());
                } else if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    payment.setStatus("PARTIAL");
                    payment.setIsPaid(false);
                } else {
                    payment.setStatus("PENDING");
                    payment.setIsPaid(false);
                    payment.setPaidAt(null);
                }

                payment.setVerifiedBy(user);
                payment.setVerifiedAt(LocalDateTime.now());
                payment.setAmountChangeComment(comment);

                // Добавляем запись в историю
                if (payment.getAmountHistory() == null) {
                    payment.setAmountHistory(new java.util.ArrayList<>());
                }

                Map<String, Object> historyEntry = new HashMap<>();
                historyEntry.put("date", LocalDateTime.now().toString());
                historyEntry.put("actor", user.getFullName());
                historyEntry.put("oldTotalPaid", oldTotalPaid);
                historyEntry.put("newTotalPaid", amount);
                historyEntry.put("comment", comment != null ? comment : "Редактирование оплаты");

                payment.getAmountHistory().add(historyEntry);

                paymentRepository.save(payment);

                String childName = getChildName(childId);
                auditLogService.log("PAYMENT_EDITED", user,
                        "Изменена оплата для ребенка \"" + childName + "\" за " +
                                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) +
                                ": " + oldTotalPaid + " ₽ → " + amount + " ₽" +
                                (comment != null ? " Комментарий: " + comment : ""));

                wsNotificationService.sendUpdateNotification("PAYMENT_EDITED");
                return "redirect:/payments?success=payment_edited";
            } else {
                return "redirect:/payments?error=payment_not_found";
            }
        } catch (Exception e) {
            return "redirect:/payments?error=" + e.getMessage();
        }
    }

    /**
     * Получает имя ребенка по ID
     */
    private String getChildName(Long childId) {
        try {
            String sql = "SELECT first_name, last_name FROM pool.children WHERE id = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, childId);
            return row.get("first_name") + " " + row.get("last_name");
        } catch (Exception e) {
            return "ребенок ID=" + childId;
        }
    }
}