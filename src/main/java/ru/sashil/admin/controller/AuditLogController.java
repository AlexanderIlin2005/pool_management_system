package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.AuditLogService;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/logs")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public String logsPage(Model model, HttpSession session,
                           @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "date_desc") String sort) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || !"ADMIN".equals(user.getRole().name())) {
            return "redirect:/login";
        }

        List<Map<String, String>> logs = auditLogService.getLogs(search, sort, 500);

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "logs");
        model.addAttribute("logs", logs);
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("sort", sort);

        return "logs";
    }

    @PostMapping("/clear")
    public String clearLogs(@RequestParam("adminPassword") String password, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return "redirect:/logs?error=wrong_password";
        }

        auditLogService.clearLogs(user);
        return "redirect:/logs?cleared=true";
    }
}