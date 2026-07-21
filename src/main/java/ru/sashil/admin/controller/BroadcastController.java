package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.BroadcastService;
import ru.sashil.admin.service.WsNotificationService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/broadcast")
public class BroadcastController {

    @Autowired
    private BroadcastService broadcastService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @GetMapping
    public String showBroadcastPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";


        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "restricted";
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "broadcast");


        model.addAttribute("isCoach", user.getRole() == AdminUser.Role.COACH);


        List<Map<String, Object>> groups = broadcastService.getAvailableGroups(user.getId(), user.getRole().name());
        model.addAttribute("groups", groups);


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


        if (user.getRole() == AdminUser.Role.COACH && "ALL".equals(targetType)) {
            return "redirect:/broadcast?error=coach_cannot_send_all";
        }

        broadcastService.createBroadcast(user, targetType, groupId, message);
        wsNotificationService.sendUpdateNotification("BROADCAST_SENDED");
        return "redirect:/broadcast?success=true";
    }
}