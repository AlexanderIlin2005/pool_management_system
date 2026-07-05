package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.ParentWithChildren;
import ru.sashil.admin.service.AdminDashboardService;
import ru.sashil.admin.service.StringSimilarityService;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class AdminController {

    private static final String SESSION_USER_KEY = "currentUser";

    @Autowired
    private AdminDashboardService dashboardService;

    @Autowired
    private StringSimilarityService similarityService;

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

        // Добавляем данные пользователя в модель
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());

        // 2. Логика таблицы только для АДМИНА
        if (currentUser.getRole() == AdminUser.Role.ADMIN) {
            List<ParentWithChildren> allParents = dashboardService.getAllParents();
            List<ParentWithChildren> filteredParents = new ArrayList<>();

            // Фильтрация (Поиск) с использованием сервиса схожести
            if (search != null && !search.isEmpty()) {
                for (ParentWithChildren p : allParents) {
                    boolean match = false;

                    // Нечеткий поиск по ФИО
                    if (similarityService.isSimilar(p.getLastName(), search, 0.7)) match = true;
                    if (similarityService.isSimilar(p.getFirstName(), search, 0.7)) match = true;
                    if (p.getMiddleName() != null && similarityService.isSimilar(p.getMiddleName(), search, 0.7)) match = true;

                    // Точный поиск по подстроке в полном имени, email, детях
                    String lowerSearch = search.toLowerCase();
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
            model.addAttribute("parents", new ArrayList<>());
        }

        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);

        return "dashboard";
    }
}