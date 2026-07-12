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

        // Бухгалтер и другие роли не имеют доступа
        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "restricted";
        }

        List<Pool> availablePools = scheduleService.getAvailablePools(user);

        // Если пулов нет или пользователь не выбрал, берем первый доступный
        if (availablePools.isEmpty()) {
            model.addAttribute("fullName", user.getFullName());
            model.addAttribute("role", user.getRole());
            model.addAttribute("activePage", "schedule");
            model.addAttribute("message", "У вас пока нет назначенных групп или бассейнов.");
            return "schedule";
        }

        // ИСПРАВЛЕНИЕ: используем финальную переменную для передачи в лямбду
        final Long selectedPoolIdFinal = (poolId != null &&
                availablePools.stream().anyMatch(p -> p.getId().equals(poolId)))
                ? poolId
                : availablePools.get(0).getId();

        Map<String, Object> scheduleData = scheduleService.getWeeklySchedule(user, selectedPoolIdFinal);

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "schedule");
        model.addAttribute("pools", availablePools);
        model.addAttribute("selectedPoolId", selectedPoolIdFinal);
        model.addAttribute("schedule", scheduleData.get("schedule"));
        model.addAttribute("currentDayIndex", scheduleData.get("currentDayIndex"));
        model.addAttribute("currentTimePercent", scheduleData.get("currentTimePercent"));
        model.addAttribute("dayStart", scheduleData.get("dayStart"));
        model.addAttribute("dayEnd", scheduleData.get("dayEnd"));

        return "schedule";
    }
}