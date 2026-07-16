package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.AdminUserService;
import ru.sashil.admin.service.AuditLogService;
import ru.sashil.admin.service.WsNotificationService;
import ru.sashil.admin.util.FileUtils;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Controller
@RequestMapping("/users")
public class UserController {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @GetMapping
    public String usersPage(Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        if (currentUser.getRole() != AdminUser.Role.ADMIN) return "restricted";

        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "users");
        model.addAttribute("users", adminUserService.getAllUsers());

        return "users";
    }

    @GetMapping("/register")
    public String registerUserPage(Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        if (currentUser.getRole() != AdminUser.Role.ADMIN) return "restricted";

        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "users");
        model.addAttribute("roles", AdminUser.Role.values());
        model.addAttribute("newUser", new AdminUser());

        return "register-user";
    }

    @PostMapping("/register")
    public String processRegister(@ModelAttribute AdminUser newUser,
                                  @RequestParam String password,
                                  HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != AdminUser.Role.ADMIN) {
            return "redirect:/login";
        }

        newUser.setPasswordHash(passwordEncoder.encode(password));
        try {
            adminUserService.saveUser(newUser);
            auditLogService.log("USER_CREATED", currentUser,
                    "Создан пользователь: " + newUser.getFullName() + " (" + newUser.getLogin() + ", роль: " + newUser.getRole() + ")");
            wsNotificationService.sendUpdateNotification("NEW_USER_HAS_REGISTERED");
        } catch (IllegalArgumentException e) {
            return "redirect:/users/register?error";
        }
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String editUserPage(@PathVariable Long id, Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        if (currentUser.getRole() != AdminUser.Role.ADMIN) return "restricted";

        Optional<AdminUser> userOpt = adminUserService.getUserById(id);
        if (userOpt.isEmpty()) return "redirect:/users";

        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "users");
        model.addAttribute("targetUser", userOpt.get());

        return "edit-user";
    }

    @PostMapping("/update-password")
    public void updatePasswordAndDownload(@RequestParam Long userId,
                                          @RequestParam(required = false) String newPassword,
                                          @RequestParam String newLogin,
                                          @RequestParam String newFullName,
                                          @RequestParam(required = false) String downloadFile,
                                          HttpServletResponse response,
                                          HttpSession session) throws IOException {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        Optional<AdminUser> userOpt = adminUserService.getUserById(userId);
        if (userOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        AdminUser targetUser = userOpt.get();
        boolean passwordChanged = false;
        String oldLogin = targetUser.getLogin();

        targetUser.setLogin(newLogin);
        targetUser.setFullName(newFullName);

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            targetUser.setPasswordHash(newPassword);
            passwordChanged = true;
        }

        adminUserService.saveUser(targetUser);
        wsNotificationService.sendUpdateNotification("USER_UPDATED");

        if (currentUser != null) {
            String details = "Обновлены данные пользователя ID=" + userId;
            if (!oldLogin.equals(newLogin)) details += ", логин изменен с '" + oldLogin + "' на '" + newLogin + "'";
            if (passwordChanged) details += ", пароль сброшен";
            auditLogService.log("USER_UPDATED", currentUser, details);
        }

        if (passwordChanged && "true".equals(downloadFile)) {
            String content = "Логин: " + newLogin + "\n" +
                    "Пароль: " + newPassword + "\n" +
                    "ФИО: " + newFullName;
            String fileNamePrefix = targetUser.getLogin() + "_password_" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
            try {
                FileUtils.sendTxtFile(response, fileNamePrefix, content);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            response.sendRedirect("/users");
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id,
                             @RequestParam String adminPassword,
                             HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != AdminUser.Role.ADMIN) {
            return "redirect:/login";
        }
        if (currentUser.getId().equals(id)) {
            return "redirect:/users?error=self";
        }
        if (!passwordEncoder.matches(adminPassword, currentUser.getPasswordHash())) {
            return "redirect:/users?error=wrong_pass";
        }

        Optional<AdminUser> toDelete = adminUserService.getUserById(id);
        String deletedUserInfo = toDelete.map(u -> u.getFullName() + " (" + u.getLogin() + ")").orElse("ID=" + id);

        adminUserService.deleteUser(id);
        auditLogService.log("USER_DELETED", currentUser,
                "Удален пользователь: " + deletedUserInfo);
        wsNotificationService.sendUpdateNotification("USER_DELETED");

        return "redirect:/users";
    }
}