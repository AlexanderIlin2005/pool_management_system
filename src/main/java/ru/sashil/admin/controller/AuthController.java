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

import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam("login") String login,
                               @RequestParam("password") String password,
                               Model model) {
        Optional<AdminUser> userOpt = userRepository.findByLogin(login);

        if (userOpt.isPresent()) {
            AdminUser user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPasswordHash())) {
                model.addAttribute("fullName", user.getFullName());
                model.addAttribute("role", user.getRole());
                return "dashboard";
            }
        }

        model.addAttribute("error", "Неверный логин или пароль");
        return "login";
    }

    // --- Новая логика регистрации ---

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

        // Проверка, занят ли логин
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

        return "redirect:/login?registered";
    }
}