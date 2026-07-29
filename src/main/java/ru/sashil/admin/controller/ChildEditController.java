package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.dto.ChildEditDto;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.ChildEditService;

@Controller
@RequestMapping("/children/edit")
public class ChildEditController {

    @Autowired
    private ChildEditService childEditService;

    @GetMapping("/{childId}")
    public String editChildPage(@PathVariable Long childId, Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        // Только администратор может редактировать данные ребенка
        if (user.getRole() != AdminUser.Role.ADMIN) {
            return "redirect:/children/profile/" + childId + "?error=access_denied";
        }

        try {
            ChildEditDto child = childEditService.getChildForEdit(childId);
            model.addAttribute("child", child);
            model.addAttribute("fullName", user.getFullName());
            model.addAttribute("role", user.getRole());
            model.addAttribute("activePage", "parents");

            // Список навыков для выбора
            String[] skills = {"не умеет", "держится на воде", "уверенно плавает"};
            model.addAttribute("skills", skills);

            return "child-edit";
        } catch (Exception e) {
            return "redirect:/parents?error=child_not_found";
        }
    }

    @PostMapping("/update")
    public String updateChild(@ModelAttribute ChildEditDto dto, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        if (user.getRole() != AdminUser.Role.ADMIN) {
            return "redirect:/children/profile/" + dto.getChildId() + "?error=access_denied";
        }

        try {
            childEditService.updateChild(dto, user);
            return "redirect:/children/profile/" + dto.getChildId() + "?success=updated";
        } catch (Exception e) {
            return "redirect:/children/edit/" + dto.getChildId() + "?error=update_failed";
        }
    }
}