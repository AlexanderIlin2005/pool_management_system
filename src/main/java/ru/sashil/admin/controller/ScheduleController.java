package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.ScheduleService;
import jakarta.servlet.http.HttpSession;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/schedule")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @GetMapping
    public String showSchedule(Model model, HttpSession session,
                               @RequestParam(required = false) Long poolId,
                               @RequestParam(defaultValue = "0") int weekOffset) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";
        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "restricted";
        }

        // Вычисляем дату начала запрашиваемой недели
        LocalDate currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(weekOffset);

        // Запрашиваем данные у сервиса СТРОГО для этой недели и выбранного бассейна
        Map<String, Object> scheduleData = scheduleService.getWeeklySchedule(user, poolId, currentWeekStart);

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "schedule");
        model.addAttribute("pools", scheduleData.get("availablePools"));
        model.addAttribute("selectedPoolId", scheduleData.get("selectedPoolId"));
        model.addAttribute("showPoolSelector", scheduleData.get("showPoolSelector"));
        model.addAttribute("schedule", scheduleData.get("schedule"));
        model.addAttribute("currentDayIndex", scheduleData.get("currentDayIndex"));
        model.addAttribute("currentTimePercent", scheduleData.get("currentTimePercent"));

        // Передаем параметры навигации
        model.addAttribute("weekOffset", weekOffset);
        model.addAttribute("weekStart", currentWeekStart);
        model.addAttribute("weekEnd", currentWeekStart.plusDays(6));

        // ДОБАВЛЯЕМ ЭТУ СТРОКУ:
        model.addAttribute("today", LocalDate.now());

        // ПЕРЕДАЕМ ДАННЫЕ КАЛЕНДАРЯ В ШАБЛОН
        model.addAttribute("holidayDates", scheduleData.get("holidayDates"));
        model.addAttribute("vacationDates", scheduleData.get("vacationDates"));

        return "schedule";
    }
}