package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.SubscriptionType;
import ru.sashil.admin.service.SubscriptionTypeService;

@Controller
@RequestMapping("/subscription-types")
public class SubscriptionTypeController {

    @Autowired
    private SubscriptionTypeService service;

    @GetMapping
    public String list(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ADMIN) return "redirect:/login";

        model.addAttribute("types", service.getAll());
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "subscription-types");
        return "subscription-types";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String name,
                       @RequestParam String displayName,
                       HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ADMIN) return "redirect:/login";

        try {
            SubscriptionType type = new SubscriptionType();
            type.setId(id);
            type.setName(name.trim().toUpperCase().replaceAll("\\s+", "_"));
            type.setDisplayName(displayName.trim());
            service.save(type);
            return "redirect:/subscription-types?success=true";
        } catch (IllegalArgumentException e) {
            return "redirect:/subscription-types?error=" + e.getMessage();
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || user.getRole() != AdminUser.Role.ADMIN) return "redirect:/login";

        try {
            service.delete(id);
            return "redirect:/subscription-types?success=deleted";
        } catch (Exception e) {
            return "redirect:/subscription-types?error=Не+удалось+удалить:+возможно+тип+используется+в+группах";
        }
    }
}