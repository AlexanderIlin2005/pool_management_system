package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.common.service.DatabaseService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/absences")
public class AbsenceController {

    @Autowired
    private DatabaseService databaseService;

    @GetMapping
    public String absencesPage(Model model, HttpSession session,
                               @RequestParam(required = false) String tab) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        String activeTab = (tab != null) ? tab : "new";
        List<Map<String, Object>> notifications;

        try {
            boolean isArchive = "archive".equals(activeTab);

            if (isArchive) {
                // Обработанные уведомления
                if (user.getRole() == AdminUser.Role.COACH) {
                    notifications = databaseService.getProcessedAbsenceNotificationsForCoachWithoutCertificate(user.getId());
                } else {
                    notifications = databaseService.getProcessedAbsenceNotificationsWithoutCertificate();
                }
            } else {
                // Новые уведомления
                if (user.getRole() == AdminUser.Role.COACH) {
                    notifications = databaseService.getAbsenceNotificationsForCoachWithoutCertificate(user.getId());
                } else {
                    notifications = databaseService.getAbsenceNotificationsWithoutCertificate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Ошибка загрузки уведомлений: " + e.getMessage());
            notifications = List.of();
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "absences");
        model.addAttribute("notifications", notifications);
        model.addAttribute("currentTab", activeTab);

        return "absences";
    }

    @PostMapping("/mark-read/{id}")
    public String markAsRead(@PathVariable Long id, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        try {
            // Используем новый метод, который одновременно ставит READ и обновляет attendance
            databaseService.processAbsenceNotificationWithoutCertificate(id, user.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/absences?success=true";
    }

}