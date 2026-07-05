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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
public class AuthController {

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
                               Model model) {
        Optional<AdminUser> userOpt = userRepository.findByLogin(login);

        if (userOpt.isPresent()) {
            AdminUser user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPasswordHash())) {
                // Сохраняем пользователя в сессии или просто передаем во view
                model.addAttribute("fullName", user.getFullName());
                model.addAttribute("role", user.getRole());

                // Если это админ, загружаем данные для таблицы
                if (user.getRole() == AdminUser.Role.ADMIN) {
                    model.addAttribute("parents", dashboardService.getAllParents());
                }

                return "dashboard";
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
    public String dashboard(Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String sortField,
                            @RequestParam(required = false) String sortOrder) {

        List<ParentWithChildren> allParents = dashboardService.getAllParents();
        List<ParentWithChildren> filteredParents = new ArrayList<>();

        // 1. Фильтрация (Поиск)
        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            for (ParentWithChildren p : allParents) {
                boolean match = false;

                // Поиск по частям ФИО родителя
                if (dashboardService.isSimilar(p.getLastName(), search, 0.7)) match = true;
                if (dashboardService.isSimilar(p.getFirstName(), search, 0.7)) match = true;
                if (p.getMiddleName() != null && dashboardService.isSimilar(p.getMiddleName(), search, 0.7)) match = true;

                // Поиск по полному ФИО
                if (p.getFullName().toLowerCase().contains(lowerSearch)) match = true;

                // Поиск по детям (Фамилия Имя)
                if (p.getChild1().toLowerCase().contains(lowerSearch)) match = true;
                if (p.getChild2().toLowerCase().contains(lowerSearch)) match = true;
                if (p.getChild3().toLowerCase().contains(lowerSearch)) match = true;

                // Поиск по email и телефону
                if (p.getEmail() != null && p.getEmail().toLowerCase().contains(lowerSearch)) match = true;
                if (p.getPhone() != null && p.getPhone().contains(search)) match = true; // Телефон ищем точным вхождением цифр

                if (match) filteredParents.add(p);
            }
        } else {
            filteredParents = allParents;
        }

        // 2. Сортировка
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
        // Сохраняем текущие параметры поиска и сортировки для форм
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);

        // Роль нужна для проверки доступа к таблице
        // В реальном приложении роль берется из SecurityContext, но пока передадим заглушку или из сессии
        // Для простоты предположим, что мы уже проверили роль при логине и просто показываем страницу

        return "dashboard";
    }
}