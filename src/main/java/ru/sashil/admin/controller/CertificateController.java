package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.common.service.DatabaseService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/certificates")
public class CertificateController {

    @Autowired
    private DatabaseService databaseService; // Используем общий сервис

    @GetMapping
    public String certificatesPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        List<Map<String, Object>> certificates;

        if (user.getRole() == AdminUser.Role.ADMIN) {
            certificates = databaseService.getUnreadCertificates();
        } else if (user.getRole() == AdminUser.Role.COACH) {
            certificates = databaseService.getUnreadCertificatesForCoach(user.getId());
        } else {
            return "restricted";
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "documents"); // Или certs, если добавишь пункт меню
        model.addAttribute("certificates", certificates);

        // Подсчет непрочитанных для бейджа в меню
        long unreadCount = certificates.size();
        model.addAttribute("unreadCertsCount", unreadCount);

        return "certificates";
    }

    @PostMapping("/process")
    public String processCertificate(@RequestParam Long certId,
                                     @RequestParam String status, // APPROVED_SICK or APPROVED_EXCUSED or REJECTED
                                     @RequestParam(required = false) LocalDate dateFrom,
                                     @RequestParam(required = false) LocalDate dateTo,
                                     HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        databaseService.processCertificate(certId, user.getId(), status, dateFrom, dateTo);
        return "redirect:/certificates?processed=true";
    }
}