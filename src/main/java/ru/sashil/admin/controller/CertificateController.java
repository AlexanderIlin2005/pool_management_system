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
    private DatabaseService databaseService;

    @GetMapping
    public String certificatesPage(Model model, HttpSession session,
                                   @RequestParam(required = false) String tab) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        List<Map<String, Object>> certificates;
        boolean isNewTab = !"archive".equals(tab);

        if (isNewTab) {

            if (user.getRole() == AdminUser.Role.COACH) {
                certificates = databaseService.getUnreadCertificatesForCoach(user.getId());
            } else {
                certificates = databaseService.getUnreadCertificates();
            }
        } else {
            certificates = databaseService.getReadCertificates();
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "documents");
        model.addAttribute("certificates", certificates);
        model.addAttribute("currentTab", isNewTab ? "new" : "archive");

        return "certificates";
    }

    @PostMapping("/process")
    public String processCertificate(@RequestParam Long certId,
                                     @RequestParam String status,
                                     @RequestParam(required = false) LocalDate dateFrom,
                                     @RequestParam(required = false) LocalDate dateTo,
                                     HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        databaseService.processCertificate(certId, user.getId(), status, dateFrom, dateTo);
        return "redirect:/certificates?success=true";
    }


    @PostMapping("/reset")
    public String resetCertificate(@RequestParam Long certId, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        databaseService.resetCertificateReadStatus(certId);
        return "redirect:/certificates?tab=archive&reset=true";
    }
}