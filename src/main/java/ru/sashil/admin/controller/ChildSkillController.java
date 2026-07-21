package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.common.service.DatabaseService;

import java.util.Map;

@Controller
@RequestMapping("/children/skill")
public class ChildSkillController {

    @Autowired
    private DatabaseService databaseService;

    @GetMapping("/edit/{childId}")
    public String editSkillPage(@PathVariable Long childId,
                                Model model,
                                HttpSession session,
                                HttpServletRequest request) { // Добавляем request для получения Referer
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        // Получаем данные ребенка
        String sql = "SELECT c.id, c.first_name, c.last_name, c.skill, p.id as parent_id " +
                "FROM pool.children c JOIN pool.parents p ON c.parent_id = p.id WHERE c.id = ?";

        Map<String, Object> childData = null;
        boolean hasAccess = false;

        try (java.sql.Connection conn = databaseService.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, childId);
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                childData = new java.util.HashMap<>();
                childData.put("id", rs.getLong("id"));
                childData.put("firstName", rs.getString("first_name"));
                childData.put("lastName", rs.getString("last_name"));
                childData.put("skill", rs.getString("skill"));
                childData.put("parentId", rs.getLong("parent_id"));

                // Проверка прав доступа
                if (user.getRole() == AdminUser.Role.ADMIN) {
                    hasAccess = true;
                } else if (user.getRole() == AdminUser.Role.COACH) {
                    String checkSql = "SELECT COUNT(*) FROM pool.group_children gc " +
                            "JOIN pool.groups g ON gc.group_id = g.id " +
                            "WHERE gc.child_id = ? AND g.trainer_id = ?";
                    try (java.sql.PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setLong(1, childId);
                        checkStmt.setLong(2, user.getId());
                        java.sql.ResultSet checkRs = checkStmt.executeQuery();
                        if (checkRs.next() && checkRs.getInt(1) > 0) {
                            hasAccess = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/parents?error=access_error";
        }

        if (!hasAccess || childData == null) {
            return "redirect:/parents?error=access_denied";
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "parents");
        model.addAttribute("child", childData);

        // Список возможных навыков
        String[] skills = {"не умеет", "держится на воде", "уверенно плавает"};
        model.addAttribute("availableSkills", skills);

        // Сохраняем Referer (страницу, с которой пришли) в модель, чтобы передать в форму
        String referer = request.getHeader("Referer");
        // Если реферер пуст или ведет на эту же страницу, ставим дефолт /parents
        if (referer == null || referer.contains("/children/skill/")) {
            referer = "/parents";
        }
        model.addAttribute("returnUrl", referer);

        return "child-skill-edit";
    }

    @PostMapping("/update")
    public String updateSkill(@RequestParam Long childId,
                              @RequestParam String newSkill,
                              @RequestParam(required = false) String returnUrl, // Получаем URL возврата из формы
                              HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        databaseService.updateChildSkill(childId, newSkill);

        // Редиректим туда, откуда пришли. Если пусто - на /parents
        if (returnUrl != null && !returnUrl.isEmpty()) {
            return "redirect:" + returnUrl;
        }
        return "redirect:/parents?success=skill_updated";
    }
}