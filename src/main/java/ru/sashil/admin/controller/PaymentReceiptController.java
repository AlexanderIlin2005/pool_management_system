package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.PaymentService;
import ru.sashil.admin.service.WsNotificationService;

import java.time.LocalDate;
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

    @GetMapping
    public String receiptsPage(Model model, HttpSession session,
                               @RequestParam(required = false) String tab) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ACCOUNTANT) {
            return "redirect:/login";
        }

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
                                 @RequestParam(required = false) String comment,
                                 HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        paymentService.approvePayment(id, user.getId(), comment);
        wsNotificationService.sendUpdateNotification("PAYMENT_APPROVED");
        return "redirect:/payment-receipts?success=approved";
    }

    @PostMapping("/{id}/reject")
    public String rejectReceipt(@PathVariable Long id,
                                @RequestParam(required = false) String comment,
                                HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        paymentService.rejectPayment(id, user.getId(), comment);
        wsNotificationService.sendUpdateNotification("PAYMENT_REJECTED");
        return "redirect:/payment-receipts?success=rejected";
    }
}