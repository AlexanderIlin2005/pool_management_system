package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.repository.AdminUserRepository;
import ru.sashil.admin.service.WsNotificationService;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;

@Controller
public class AuthController {

    private static final String SESSION_USER_KEY = "currentUser";

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private WsNotificationService wsNotificationService;

    @GetMapping("/")
    public String rootRedirect(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute(SESSION_USER_KEY);

        if (user != null) {
            // Если пользователь уже авторизован — редирект по роли
            if (user.getRole() == AdminUser.Role.COACH) {
                return "redirect:/schedule";
            } else if (user.getRole() == AdminUser.Role.ACCOUNTANT) {
                return "redirect:/payments";
            } else {
                return "redirect:/parents";
            }
        }

        // Если не авторизован — на страницу логина
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam("login") String login,
                               @RequestParam("password") String password,
                               HttpSession session,
                               Model model) {
        Optional<AdminUser> userOpt = userRepository.findByLogin(login);

        if (userOpt.isPresent()) {
            AdminUser user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPasswordHash())) {
                session.setAttribute(SESSION_USER_KEY, user);


                // Редирект по ролям
                if (user.getRole() == AdminUser.Role.COACH) {
                    return "redirect:/schedule";
                } else if (user.getRole() == AdminUser.Role.ACCOUNTANT) {
                    return "redirect:/payments";
                } else {
                    return "redirect:/parents";
                }
            }
        }

        model.addAttribute("error", "Неверный логин или пароль");
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("roles", AdminUser.Role.values());
        return "register";
    }

    @PostMapping("/register")
    public String processRegister(@RequestParam("login") String login,
                                  @RequestParam("password") String password,
                                  @RequestParam("fullName") String fullName,
                                  @RequestParam("role") AdminUser.Role role,
                                  Model model) {

        if (userRepository.findByLogin(login).isPresent()) {
            model.addAttribute("error", "Этот логин уже занят");
            model.addAttribute("roles", AdminUser.Role.values());
            return "register";
        }

        AdminUser newUser = new AdminUser();
        newUser.setLogin(login);
        newUser.setPasswordHash(passwordEncoder.encode(password));
        newUser.setFullName(fullName);
        newUser.setRole(role);

        userRepository.save(newUser);
        wsNotificationService.sendUpdateNotification("NEW_USER_SELF_REGISTERED");

        return "redirect:/login?registered";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}