package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.ParentWithChildren;
import ru.sashil.admin.service.AdminDashboardService;
import ru.sashil.admin.service.GroupService;
import ru.sashil.admin.service.StringSimilarityService;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
public class AdminController {

    @Autowired
    private AdminDashboardService dashboardService;

    @Autowired
    private StringSimilarityService similarityService;

    @Autowired
    private GroupService groupService;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session,
                            Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String sortField,
                            @RequestParam(required = false) String sortOrder) {

        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";

        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "dashboard");

        if (currentUser.getRole() == ru.sashil.admin.model.AdminUser.Role.ADMIN) {
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
        } else {
            model.addAttribute("parents", new ArrayList<>());
        }

        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);

        return "dashboard";
    }

    @GetMapping("/groups")
    public String groupsPage(Model model, HttpSession session,
                             @RequestParam(required = false) String search,
                             @RequestParam(required = false) String sortField,
                             @RequestParam(required = false) String sortOrder) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");

        List<Group> allGroups = groupService.getAllGroups();
        List<Group> filteredGroups = new ArrayList<>();

        // Поиск по группам
        if (search != null && !search.isEmpty()) {
            for (Group g : allGroups) {
                boolean match = false;
                String lowerSearch = search.toLowerCase();

                // Нечеткий поиск по названию группы
                if (similarityService.isSimilar(g.getName(), search, 0.7)) match = true;

                // Поиск по номеру (точное совпадение или содержит)
                if (g.getNumber().toString().contains(search)) match = true;

                // Поиск по бассейну
                if (g.getPool() != null && g.getPool().getName().toLowerCase().contains(lowerSearch)) match = true;

                if (match) filteredGroups.add(g);
            }
        } else {
            filteredGroups = allGroups;
        }

        // Сортировка групп
        if (sortField != null) {
            Comparator<Group> comparator = null;
            switch (sortField) {
                case "number": comparator = Comparator.comparing(Group::getNumber); break;
                case "name": comparator = Comparator.comparing(Group::getName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                case "pool": comparator = Comparator.comparing(g -> g.getPool() != null ? g.getPool().getName() : "", Comparator.nullsLast(String::compareToIgnoreCase)); break;
                default: comparator = Comparator.comparing(Group::getId);
            }

            if ("desc".equals(sortOrder)) {
                comparator = comparator.reversed();
            }
            filteredGroups.sort(comparator);
        }

        model.addAttribute("groups", filteredGroups);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);

        return "groups";
    }

    @GetMapping("/groups/new")
    public String newGroupPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("pools", groupService.getAllPools());
        model.addAttribute("group", new Group());
        return "new-group";
    }

    @PostMapping("/groups/save")
    public String saveGroup(@ModelAttribute Group group, Model model, HttpSession session) {
        try {
            groupService.saveGroup(group);
            return "redirect:/groups?success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("pools", groupService.getAllPools());
            model.addAttribute("fullName", ((AdminUser) session.getAttribute("currentUser")).getFullName());
            model.addAttribute("role", ((AdminUser) session.getAttribute("currentUser")).getRole());
            model.addAttribute("activePage", "groups");
            return "new-group";
        }
    }


    @GetMapping("/groups/edit/{id}")
    public String editGroupPage(@PathVariable Long id, Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Optional<Group> groupOpt = groupService.getGroupById(id);
        if (groupOpt.isEmpty()) return "redirect:/groups";

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("pools", groupService.getAllPools());
        model.addAttribute("group", groupOpt.get()); // Загружаем существующие данные
        model.addAttribute("isEdit", true); // Флаг, чтобы понимать, что мы редактируем
        return "new-group"; // Используем тот же шаблон формы
    }

    @PostMapping("/groups/delete/{id}")
    public String deleteGroup(@PathVariable Long id, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        groupService.deleteGroup(id);
        return "redirect:/groups";
    }


}