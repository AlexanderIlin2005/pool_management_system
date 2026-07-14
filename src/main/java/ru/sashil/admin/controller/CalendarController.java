package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.CalendarService;

import java.time.LocalDate;

@Controller
@RequestMapping("/calendar")
public class CalendarController {

    @Autowired
    private CalendarService calendarService;

    @GetMapping
    public String showCalendar(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ADMIN) {
            return "redirect:/login";
        }

        model.addAttribute("holidays", calendarService.getAllHolidays());
        model.addAttribute("vacations", calendarService.getAllVacations());
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "calendar");

        return "calendar";
    }

    @PostMapping("/add-holiday")
    public String addHoliday(@RequestParam LocalDate date, @RequestParam String name) {
        calendarService.addHoliday(date, name);
        return "redirect:/calendar";
    }

    @PostMapping("/delete-holiday/{id}")
    public String deleteHoliday(@PathVariable Long id) {
        calendarService.deleteHoliday(id);
        return "redirect:/calendar";
    }

    @PostMapping("/add-vacation")
    public String addVacation(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam String name) {
        calendarService.addVacation(startDate, endDate, name);
        return "redirect:/calendar";
    }

    @PostMapping("/delete-vacation/{id}")
    public String deleteVacation(@PathVariable Long id) {
        calendarService.deleteVacation(id);
        return "redirect:/calendar";
    }
}