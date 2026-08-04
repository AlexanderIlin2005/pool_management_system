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
                                   @RequestParam(required = false) String tab,
                                   @RequestParam(required = false) String type) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        List<Map<String, Object>> certificates;
        boolean isNewTab = !"archive".equals(tab);
        boolean isAbsence = "absence".equals(type);

        try {
            if (isAbsence) {
                // Справки о болезни из absence_notifications
                if (user.getRole() == AdminUser.Role.COACH) {
                    certificates = databaseService.getAbsenceCertificatesForCoach(user.getId());
                } else {
                    certificates = databaseService.getAllAbsenceCertificates();
                }
            } else {
                // Обычные справки о допуске из certificates
                if (isNewTab) {
                    if (user.getRole() == AdminUser.Role.COACH) {
                        certificates = databaseService.getUnreadCertificatesForCoach(user.getId());
                    } else {
                        certificates = databaseService.getUnreadCertificates();
                    }
                } else {
                    certificates = databaseService.getReadCertificates();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Ошибка загрузки справок: " + e.getMessage());
            certificates = List.of(); // Пустой список при ошибке, чтобы не было NPE в шаблоне
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "certificates");
        model.addAttribute("certificates", certificates);
        model.addAttribute("currentTab", isNewTab ? "new" : "archive");
        model.addAttribute("certType", isAbsence ? "absence" : "regular");

        return "certificates";
    }

    @PostMapping("/process")
    public String processCertificate(@RequestParam Long certId,
                                     @RequestParam String status,
                                     @RequestParam(required = false) LocalDate dateFrom,
                                     @RequestParam(required = false) LocalDate dateTo,
                                     @RequestParam(required = false) String certType,
                                     HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        try {
            if ("absence".equals(certType)) {
                // Для справок о болезни - даты обязательны
                if (dateFrom == null || dateTo == null) {
                    return "redirect:/certificates?type=absence&error=date_required";
                }
                databaseService.processAbsenceCertificate(certId, user.getId(), status, dateFrom, dateTo);
                return "redirect:/certificates?type=absence&success=true";
            } else {
                // Обычная справка о допуске - даты не нужны, просто меняем статус и is_read
                databaseService.processRegularCertificate(certId, user.getId(), status);
                return "redirect:/certificates?tab=archive&success=true";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/certificates?error=" + e.getMessage();
        }
    }

    @PostMapping("/reject")
    public String rejectCertificate(@RequestParam Long certId,
                                    @RequestParam(required = false) String comment,
                                    @RequestParam(required = false) String certType,
                                    HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        try {
            if ("absence".equals(certType)) {
                databaseService.rejectAbsenceCertificate(certId, user.getId(), comment);
                return "redirect:/certificates?type=absence&success=rejected";
            } else {
                databaseService.rejectCertificate(certId, user.getId(), comment);
                return "redirect:/certificates?success=rejected";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/certificates?error=" + e.getMessage();
        }
    }

    @PostMapping("/reset")
    public String resetCertificate(@RequestParam Long certId,
                                   @RequestParam(required = false) String certType,
                                   HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        try {
            if ("absence".equals(certType)) {
                databaseService.resetAbsenceCertificate(certId);
                return "redirect:/certificates?type=absence&reset=true";
            } else {
                databaseService.resetCertificateReadStatus(certId);
                return "redirect:/certificates?tab=new&reset=true";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/certificates?error=" + e.getMessage();
        }
    }
}