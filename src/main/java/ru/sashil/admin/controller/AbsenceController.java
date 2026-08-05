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
    public String absencesPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        List<Map<String, Object>> notifications;
        try {
            if (user.getRole() == AdminUser.Role.COACH) {
                notifications = databaseService.getAbsenceNotificationsForCoachWithoutCertificate(user.getId());
            } else {
                notifications = databaseService.getAbsenceNotificationsWithoutCertificate();
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

        return "absences";
    }

    @PostMapping("/mark-read/{id}")
    public String markAsRead(@PathVariable Long id, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        try {
            databaseService.markAbsenceNotificationAsRead(id, user.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/absences?success=true";
    }
}