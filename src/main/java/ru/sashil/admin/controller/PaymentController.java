package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.model.PaymentNotification;
import ru.sashil.admin.model.Payment;
import ru.sashil.admin.repository.PaymentNotificationRepository;
import ru.sashil.admin.service.GroupService;
import ru.sashil.admin.service.PaymentService;
import ru.sashil.admin.service.WsNotificationService;
import ru.sashil.admin.service.AuditLogService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @Autowired
    private PaymentNotificationRepository paymentNotificationRepository;

    @Autowired
    private AuditLogService auditLogService;

    private static final LocalDate MIN_DATE = LocalDate.of(2026, 9, 1);

    @GetMapping
    public String paymentsPage(Model model, HttpSession session,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) Integer year,
                               @RequestParam(required = false) Integer month) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        LocalDate startMonth;
        LocalDate endMonth;

        if (year != null && month != null) {
            startMonth = LocalDate.of(year, month, 1);
            endMonth = startMonth.plusMonths(10);

            if (startMonth.isBefore(MIN_DATE)) {
                return "redirect:/payments";
            }
        } else {
            startMonth = MIN_DATE;
            endMonth = startMonth.plusMonths(10);
        }

        LocalDate current = startMonth;
        while (!current.isAfter(endMonth)) {
            if (!current.isBefore(LocalDate.now().minusMonths(3).withDayOfMonth(1))) {
                paymentService.generatePaymentsForMonth(current);
            }
            current = current.plusMonths(1);
        }

        Map<String, Object> data = paymentService.getPaymentTableData(startMonth, endMonth, search);

        boolean canGoBack = startMonth.minusMonths(1).isAfter(MIN_DATE) ||
                startMonth.minusMonths(1).isEqual(MIN_DATE);

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "payments");
        model.addAttribute("months", data.get("months"));
        model.addAttribute("rows", data.get("rows"));
        model.addAttribute("currentSearch", search);
        model.addAttribute("startMonth", startMonth);
        model.addAttribute("endMonth", endMonth);
        model.addAttribute("prevStart", startMonth.minusMonths(1));
        model.addAttribute("nextStart", startMonth.plusMonths(1));
        model.addAttribute("canGoBack", canGoBack);
        model.addAttribute("monthFormatter", DateTimeFormatter.ofPattern("MMM yyyy"));
        model.addAttribute("defaultAmount", BigDecimal.ZERO); // Всегда 0

        return "payments";
    }

    /**
     * Ручное добавление суммы оплаты для конкретного ребенка и месяца
     */
    @PostMapping("/add-amount")
    public String addPaymentAmount(@RequestParam Long childId,
                                   @RequestParam String monthYear,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(required = false) String comment,
                                   HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "redirect:/payments?error=amount_positive_required";
        }

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
     */
    @PostMapping("/set-amount")
    public String setPaymentAmount(@RequestParam Long childId,
                                   @RequestParam String monthYear,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(required = false) String comment,
                                   HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "redirect:/payments?error=amount_negative_required";
        }

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
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        paymentService.approvePayment(id, user.getId(), comment);
        wsNotificationService.sendUpdateNotification("PAYMENT_APPROVED");
        return "redirect:/payments?success=approved";
    }

    @PostMapping("/{id}/reject")
    public String rejectPayment(@PathVariable Long id,
                                @RequestParam(required = false) String comment,
                                HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        paymentService.rejectPayment(id, user.getId(), comment);
        wsNotificationService.sendUpdateNotification("PAYMENT_REJECTED");
        return "redirect:/payments?success=rejected";
    }

    @PostMapping("/upload-receipt")
    public String uploadReceipt(@RequestParam Long childId,
                                @RequestParam String monthYear,
                                @RequestParam MultipartFile file,
                                HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        try {
            LocalDate month = LocalDate.parse(monthYear);
            paymentService.uploadReceipt(childId, month, file, user.getId());
            wsNotificationService.sendUpdateNotification("RECEIPT_UPLOADED");
            return "redirect:/payments?success=receipt_uploaded";
        } catch (Exception e) {
            return "redirect:/payments?error=upload_failed";
        }
    }

    @PostMapping("/send-reminder/{parentVkId}")
    public String sendReminder(@PathVariable Long parentVkId,
                               @RequestParam Long childId,
                               @RequestParam(required = false) String comment,
                               HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";
        if (user.getRole() != AdminUser.Role.ACCOUNTANT) return "redirect:/login";

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
     */
    @PostMapping("/update-amount-for-month")
    public String updateAmountForMonth(@RequestParam String monthYear,
                                       @RequestParam BigDecimal amount,
                                       @RequestParam String confirmation,
                                       HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        // Проверяем подтверждение
        if (!"ПОДТВЕРЖДАЮ".equalsIgnoreCase(confirmation.trim())) {
            return "redirect:/payments?error=confirmation_required";
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "redirect:/payments?error=amount_negative_required";
        }

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
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

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
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        try {
            paymentService.setDefaultAmount(amount, user);
            return "redirect:/payments/settings?success=true";
        } catch (Exception e) {
            return "redirect:/payments/settings?error=" + e.getMessage();
        }
    }

    /**
     * Получение списка всех уведомлений об оплате
     */
    @GetMapping("/notifications")
    public String notificationsPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        List<PaymentNotification> pendingNotifications = paymentService.getPendingNotifications();
        model.addAttribute("pendingNotifications", pendingNotifications);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "payment-notifications");

        return "payment-notifications";
    }

    /**
     * Отправка уведомления родителю
     */
    @PostMapping("/notifications/{id}/send")
    public String sendNotification(@PathVariable Long id, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        try {
            paymentService.markNotificationSent(id);
            return "redirect:/payments/notifications?success=notification_sent";
        } catch (Exception e) {
            return "redirect:/payments/notifications?error=" + e.getMessage();
        }
    }
}