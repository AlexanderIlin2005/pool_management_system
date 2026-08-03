package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Message;
import ru.sashil.admin.service.MessageService;
import ru.sashil.admin.service.AuditLogService;
import ru.sashil.admin.service.WsNotificationService;

import java.util.List;

@Controller
@RequestMapping("/messages")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @GetMapping
    public String messagesPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "restricted";
        }

        List<Message> messages;
        if (user.getRole() == AdminUser.Role.ADMIN) {
            messages = messageService.getActiveMessagesForAdmin();
        } else {
            messages = messageService.getActiveMessagesForCoach(user.getId());
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "messages");
        model.addAttribute("messages", messages);

        return "messages";
    }

    @PostMapping("/{id}/read")
    public String markAsRead(@PathVariable Long id, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        messageService.markMessageAsRead(id);
        wsNotificationService.sendUpdateNotification("MESSAGE_READ");

        return "redirect:/messages";
    }

    @PostMapping("/{id}/reply")
    public String replyToMessage(@PathVariable Long id,
                                 @RequestParam String replyText,
                                 HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        String userType = user.getRole() == AdminUser.Role.ADMIN ? "ADMIN" : "COACH";

        try {
            messageService.replyToMessage(id, user.getId(), userType, replyText);

            auditLogService.log("MESSAGE_REPLIED", user,
                    "Ответ на сообщение ID=" + id + " от пользователя " + user.getFullName());
            wsNotificationService.sendUpdateNotification("MESSAGE_REPLIED");

            return "redirect:/messages?success=true";
        } catch (Exception e) {
            return "redirect:/messages?error=" + e.getMessage();
        }
    }
}