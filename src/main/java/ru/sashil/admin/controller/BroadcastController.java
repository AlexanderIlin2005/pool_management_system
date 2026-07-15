package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.BroadcastService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/broadcast")
public class BroadcastController {

    @Autowired
    private BroadcastService broadcastService;

    @GetMapping
    public String showBroadcastPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        // Доступно Админу и Тренеру
        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "restricted";
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "broadcast");

        // Флаг: является ли пользователь тренером (для скрытия опции "Всем")
        model.addAttribute("isCoach", user.getRole() == AdminUser.Role.COACH);

        // Список групп для выбора
        List<Map<String, Object>> groups = broadcastService.getAvailableGroups(user.getId(), user.getRole().name());
        model.addAttribute("groups", groups);

        // История
        model.addAttribute("history", broadcastService.getBroadcastHistory());

        return "broadcast";
    }

    @PostMapping("/send")
    public String sendBroadcast(@RequestParam String targetType,
                                @RequestParam(required = false) Long groupId,
                                @RequestParam String message,
                                HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        // Дополнительная защита: если тренер пытается отправить всем, блокируем или меняем на группу по умолчанию
        if (user.getRole() == AdminUser.Role.COACH && "ALL".equals(targetType)) {
            return "redirect:/broadcast?error=coach_cannot_send_all";
        }

        broadcastService.createBroadcast(user, targetType, groupId, message);
        return "redirect:/broadcast?success=true";
    }
}