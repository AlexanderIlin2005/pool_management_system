package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.common.service.DatabaseService;

import java.time.LocalDate;
import java.util.ArrayList;
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

        List<Map<String, Object>> certificates = new ArrayList<>();
        String activeTab = (tab != null) ? tab : "new";

        // Счетчики для вкладок
        int newCount = 0;
        int absenceCount = 0;

        try {
            // Получаем счетчики для вкладок
            if (user.getRole() == AdminUser.Role.COACH) {
                newCount = databaseService.getUnreadCertificatesForCoach(user.getId()).size();
                absenceCount = databaseService.getAbsenceCertificatesForCoach(user.getId()).size();
            } else {
                newCount = databaseService.getUnreadCertificates().size();
                absenceCount = databaseService.getAllAbsenceCertificates().size();
            }

            switch (activeTab) {
                case "absence":
                    if (user.getRole() == AdminUser.Role.COACH) {
                        certificates = databaseService.getAbsenceCertificatesForCoach(user.getId());
                    } else {
                        certificates = databaseService.getAllAbsenceCertificates();
                    }
                    break;
                case "archive":
                    List<Map<String, Object>> readRegular = databaseService.getReadCertificates();
                    List<Map<String, Object>> readAbsence = databaseService.getProcessedAbsenceCertificates();

                    certificates.addAll(readRegular);
                    certificates.addAll(readAbsence);

                    certificates.sort((a, b) -> {
                        java.sql.Timestamp t1 = (java.sql.Timestamp) a.get("uploaded_at");
                        java.sql.Timestamp t2 = (java.sql.Timestamp) b.get("uploaded_at");
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    });
                    break;
                case "new":
                default:
                    if (user.getRole() == AdminUser.Role.COACH) {
                        certificates = databaseService.getUnreadCertificatesForCoach(user.getId());
                    } else {
                        certificates = databaseService.getUnreadCertificates();
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Ошибка загрузки справок: " + e.getMessage());
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "certificates");
        model.addAttribute("certificates", certificates);
        model.addAttribute("currentTab", activeTab);
        model.addAttribute("newCount", newCount);
        model.addAttribute("absenceCount", absenceCount);

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
                if (dateFrom == null || dateTo == null) {
                    return "redirect:/certificates?tab=absence&error=date_required";
                }
                databaseService.processAbsenceCertificate(certId, user.getId(), status, dateFrom, dateTo);
                return "redirect:/certificates?tab=absence&success=true";
            } else {
                databaseService.processRegularCertificate(certId, user.getId(), status);
                return "redirect:/certificates?tab=new&success=true";
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
                return "redirect:/certificates?tab=absence&success=rejected";
            } else {
                databaseService.rejectCertificate(certId, user.getId(), comment);
                return "redirect:/certificates?tab=new&success=rejected";
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
                return "redirect:/certificates?tab=absence&reset=true";
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