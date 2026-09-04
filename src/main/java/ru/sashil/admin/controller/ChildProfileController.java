package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.dto.ChildProfileDto;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.ChildProfileService;
import ru.sashil.admin.service.GroupMemberService;
import ru.sashil.admin.service.WsNotificationService;

@Controller
@RequestMapping("/children")
public class ChildProfileController {

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private WsNotificationService wsNotificationService;

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

    /**
     * Исключает ребенка из группы (AJAX запрос из профиля ребенка)
     */
    @PostMapping("/remove-from-group")
    @ResponseBody
    public String removeChildFromGroup(@RequestParam Long childId,
                                       @RequestParam Long groupId,
                                       HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "error: not authenticated";

        // Только ADMIN может исключать из группы
        if (user.getRole() != AdminUser.Role.ADMIN) {
            return "error: access denied";
        }

        try {
            // Удаляем ребенка из группы
            groupMemberService.removeChildFromGroup(groupId, childId, user);
            wsNotificationService.sendUpdateNotification("CHILD_REMOVED_FROM_GROUP");
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "error: " + e.getMessage();
        }
    }
}