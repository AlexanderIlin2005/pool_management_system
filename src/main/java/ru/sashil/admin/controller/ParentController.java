package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.ParentWithChildren;
import ru.sashil.admin.service.AdminDashboardService;
import ru.sashil.admin.service.StringSimilarityService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/parents")
public class ParentController {

    @Autowired
    private AdminDashboardService dashboardService;

    @Autowired
    private StringSimilarityService similarityService;


    private boolean isAdmin(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        return user != null && user.getRole() == ru.sashil.admin.model.AdminUser.Role.ADMIN;
    }

    // Проверка доступа для ADMIN и COACH
    private boolean hasAccess(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        return user != null && (user.getRole() == AdminUser.Role.ADMIN || user.getRole() == AdminUser.Role.COACH);
    }

    @GetMapping
    public String parentsPage(HttpSession session, Model model,
                              @RequestParam(required = false) String search,
                              @RequestParam(required = false) String sortField,
                              @RequestParam(required = false) String sortOrder,
                              HttpServletRequest request) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";

        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "parents");
        model.addAttribute("currentUri", request.getRequestURI()); // Добавлен URI

        if (!hasAccess(session)) return "restricted";

        List<ParentWithChildren> allParents = dashboardService.getAllParents();
        List<ParentWithChildren> filteredParents = new ArrayList<>();

        if (search != null && !search.isEmpty()) {
            for (ParentWithChildren p : allParents) {
                boolean match = false;
                if (similarityService.isSimilar(p.getLastName(), search, 0.7)) match = true;
                if (similarityService.isSimilar(p.getFirstName(), search, 0.7)) match = true;
                if (p.getMiddleName() != null && similarityService.isSimilar(p.getMiddleName(), search, 0.7)) match = true;

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

        if (sortField != null) {
            Comparator<ParentWithChildren> comparator = null;
            switch (sortField) {
                case "lastName": comparator = Comparator.comparing(ParentWithChildren::getLastName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                case "firstName": comparator = Comparator.comparing(ParentWithChildren::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                case "fullName": comparator = Comparator.comparing(ParentWithChildren::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                default: comparator = Comparator.comparing(ParentWithChildren::getId);
            }
            if ("desc".equals(sortOrder)) comparator = comparator.reversed();
            filteredParents.sort(comparator);
        }

        model.addAttribute("parents", filteredParents);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);

        return "parents";
    }
}