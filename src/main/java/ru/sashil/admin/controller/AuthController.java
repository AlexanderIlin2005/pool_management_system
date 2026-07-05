package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.ParentWithChildren;
import ru.sashil.admin.repository.AdminUserRepository;
import ru.sashil.admin.service.AdminDashboardService;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
public class AuthController {

    private static final String SESSION_USER_KEY = "currentUser";

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private AdminDashboardService dashboardService;

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
                // Сохраняем пользователя в сессии
                session.setAttribute(SESSION_USER_KEY, user);

                // Перенаправляем на дашборд, а не просто рендерим view
                return "redirect:/dashboard";
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

        return "redirect:/login?registered";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session,
                            Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String sortField,
                            @RequestParam(required = false) String sortOrder) {

        // 1. Проверка авторизации
        AdminUser currentUser = (AdminUser) session.getAttribute(SESSION_USER_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Добавляем данные пользователя в модель для отображения в шапке
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());

        // 2. Логика таблицы только для АДМИНА
        if (currentUser.getRole() == AdminUser.Role.ADMIN) {
            List<ParentWithChildren> allParents = dashboardService.getAllParents();
            List<ParentWithChildren> filteredParents = new ArrayList<>();

            // Фильтрация (Поиск)
            if (search != null && !search.isEmpty()) {
                String lowerSearch = search.toLowerCase();
                for (ParentWithChildren p : allParents) {
                    boolean match = false;
                    if (dashboardService.isSimilar(p.getLastName(), search, 0.7)) match = true;
                    if (dashboardService.isSimilar(p.getFirstName(), search, 0.7)) match = true;
                    if (p.getMiddleName() != null && dashboardService.isSimilar(p.getMiddleName(), search, 0.7)) match = true;
                    if (p.getFullName().toLowerCase().contains(lowerSearch)) match = true;
                    if (p.getChild1().toLowerCase().contains(lowerSearch)) match = true;
                    if (p.getChild2().toLowerCase().contains(lowerSearch)) match = true;
                    if (p.getChild3().toLowerCase().contains(lowerSearch)) match = true;
                    if (p.getEmail() != null && p.getEmail().toLowerCase().contains(lowerSearch)) match = true;
                    if (p.getPhone() != null && p.getPhone().contains(search)) match = true;

                    if (match) filteredParents.add(p);
                }
            } else {
                filteredParents = allParents;
            }

            // Сортировка
            if (sortField != null) {
                Comparator<ParentWithChildren> comparator = null;
                switch (sortField) {
                    case "lastName": comparator = Comparator.comparing(ParentWithChildren::getLastName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                    case "firstName": comparator = Comparator.comparing(ParentWithChildren::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                    case "fullName": comparator = Comparator.comparing(ParentWithChildren::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                    default: comparator = Comparator.comparing(ParentWithChildren::getId);
                }
                if ("desc".equals(sortOrder)) {
                    comparator = comparator.reversed();
                }
                filteredParents.sort(comparator);
            }

            model.addAttribute("parents", filteredParents);
        } else {
            // Для не-админов список пустой (или можно добавить сообщение "Доступ ограничен")
            model.addAttribute("parents", new ArrayList<>());
        }

        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);

        return "dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // Уничтожаем сессию
        return "redirect:/login";
    }
}