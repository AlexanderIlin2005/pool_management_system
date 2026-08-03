package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.PaymentService;
import ru.sashil.admin.service.WsNotificationService;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/payment-receipts")
public class PaymentReceiptController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WsNotificationService wsNotificationService;

    /**
     * Проверяет, имеет ли пользователь доступ к бухгалтерским разделам
     */
    private boolean hasAccountingAccess(HttpSession session) {
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
    public String receiptsPage(Model model, HttpSession session,
                               @RequestParam(required = false) String tab) {

        if (!hasAccountingAccess(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        List<Map<String, Object>> receipts;
        boolean isNewTab = !"archive".equals(tab);

        if (isNewTab) {
            receipts = paymentService.getPendingReceipts();
        } else {
            receipts = paymentService.getProcessedReceipts();
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "payment-receipts");
        model.addAttribute("receipts", receipts);
        model.addAttribute("currentTab", isNewTab ? "new" : "archive");
        model.addAttribute("monthFormatter", DateTimeFormatter.ofPattern("MM.yyyy"));

        return "payment-receipts";
    }

    @PostMapping("/{id}/approve")
    public String approveReceipt(@PathVariable Long id,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam(required = false) String comment,
                                 HttpSession session) {
        if (!hasAccountingAccess(session)) {
            return "redirect:/login";
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "redirect:/payment-receipts?error=amount_positive_required";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        try {
            paymentService.approvePaymentWithAmount(id, amount, user.getId(), comment);
            wsNotificationService.sendUpdateNotification("PAYMENT_APPROVED");
            return "redirect:/payment-receipts?success=approved";
        } catch (Exception e) {
            return "redirect:/payment-receipts?error=" + e.getMessage();
        }
    }

    @PostMapping("/{id}/reject")
    public String rejectReceipt(@PathVariable Long id,
                                @RequestParam(required = false) String comment,
                                HttpSession session) {
        if (!hasAccountingAccess(session)) {
            return "redirect:/login";
        }

        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        paymentService.rejectPayment(id, user.getId(), comment);
        wsNotificationService.sendUpdateNotification("PAYMENT_REJECTED");
        return "redirect:/payment-receipts?success=rejected";
    }
}