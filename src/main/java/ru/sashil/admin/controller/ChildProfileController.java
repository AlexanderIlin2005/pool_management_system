package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.sashil.admin.dto.ChildProfileDto;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.ChildProfileService;

@Controller
@RequestMapping("/children")
public class ChildProfileController {

    @Autowired
    private ChildProfileService childProfileService;

    @GetMapping("/profile/{childId}")
    public String childProfile(@PathVariable Long childId, Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        // Проверка доступа: только ADMIN и COACH
        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "restricted";
        }

        try {
            ChildProfileDto profile = childProfileService.getChildProfile(childId);
            model.addAttribute("profile", profile);
            model.addAttribute("fullName", user.getFullName());
            model.addAttribute("role", user.getRole());
            model.addAttribute("activePage", "parents");
            // Добавляем currentUri для кнопки "Назад"
            model.addAttribute("currentUri", "/parents");

            return "child-profile";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/parents?error=child_not_found";
        }
    }
}