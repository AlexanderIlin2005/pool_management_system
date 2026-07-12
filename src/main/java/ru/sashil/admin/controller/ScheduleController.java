package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.service.ScheduleService;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/schedule")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @GetMapping
    public String showSchedule(Model model, HttpSession session,
                               @RequestParam(required = false) Long poolId) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "restricted";
        }

        List<Pool> availablePools = scheduleService.getAvailablePools(user);

        // Для тренера переключатель не нужен, он видит всё сразу
        boolean showPoolSelector = (user.getRole() == AdminUser.Role.ADMIN && availablePools.size() > 1);

        Long selectedPoolId = null;
        if (user.getRole() == AdminUser.Role.ADMIN) {
            // Логика выбора бассейна для админа
            if (availablePools.isEmpty()) {
                model.addAttribute("message", "Нет доступных бассейнов.");
                return renderBaseModel(model, user, "schedule");
            }

            final Long finalPoolId = poolId;
            selectedPoolId = availablePools.stream()
                    .anyMatch(p -> p.getId().equals(finalPoolId))
                    ? finalPoolId
                    : availablePools.get(0).getId();

        } else {
            // Тренер получает null, сервис вернет все его группы
            selectedPoolId = null;
        }

        Map<String, Object> scheduleData = scheduleService.getWeeklySchedule(user, selectedPoolId);

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "schedule");
        model.addAttribute("pools", availablePools);
        model.addAttribute("selectedPoolId", selectedPoolId);
        model.addAttribute("showPoolSelector", showPoolSelector); // Флаг для шаблона
        model.addAttribute("schedule", scheduleData.get("schedule"));
        model.addAttribute("currentDayIndex", scheduleData.get("currentDayIndex"));
        model.addAttribute("currentTimePercent", scheduleData.get("currentTimePercent"));

        return "schedule";
    }

    private String renderBaseModel(Model model, AdminUser user, String viewName) {
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "schedule");
        return viewName;
    }
}