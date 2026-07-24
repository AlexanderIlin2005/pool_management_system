package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.PaymentNotification;
import ru.sashil.admin.model.Payment;
import ru.sashil.admin.repository.PaymentNotificationRepository;
import ru.sashil.admin.service.GroupService;
import ru.sashil.admin.service.PaymentService;
import ru.sashil.admin.service.WsNotificationService;

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

    @GetMapping
    public String paymentsPage(Model model, HttpSession session,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) Integer year,
                               @RequestParam(required = false) Integer month) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        // Определяем начальный и конечный месяц (август 2026 - май 2027)
        LocalDate startMonth = LocalDate.of(2026, 8, 1);
        LocalDate endMonth = LocalDate.of(2027, 5, 1);

        // Если переданы год и месяц, используем их для навигации
        if (year != null && month != null) {
            LocalDate newStart = LocalDate.of(year, month, 1);
            // Сдвигаем окно на 10 месяцев вперед
            endMonth = newStart.plusMonths(10);
            startMonth = newStart;
        }

        // Генерируем оплаты за период, если их нет
        LocalDate current = startMonth;
        while (!current.isAfter(endMonth)) {
            paymentService.generatePaymentsForMonth(current);
            current = current.plusMonths(1);
        }

        Map<String, Object> data = paymentService.getPaymentTableData(startMonth, endMonth, search, null);
        List<Group> groups = groupService.getAllGroups();

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

        // Для отображения названий месяцев
        model.addAttribute("monthFormatter", DateTimeFormatter.ofPattern("MMM yyyy"));

        return "payments";
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

        // Создаем уведомление для бота
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
     * Обновляет сумму оплаты.
     * ИСПРАВЛЕНО: используем существующие методы PaymentService
     */
    @PostMapping("/update-amount")
    public String updateAmount(@RequestParam Long childId,
                               @RequestParam String monthYear,
                               @RequestParam BigDecimal amount,
                               HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

        try {
            LocalDate month = LocalDate.parse(monthYear + "-01"); // monthYear в формате YYYY-MM
            paymentService.updatePaymentAmount(childId, month, amount);
            wsNotificationService.sendUpdateNotification("PAYMENT_AMOUNT_UPDATED");
            return "redirect:/payments?success=amount_updated";
        } catch (Exception e) {
            return "redirect:/payments?error=amount_update_failed";
        }
    }
}