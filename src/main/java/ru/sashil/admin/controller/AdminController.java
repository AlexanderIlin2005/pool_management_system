package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.ParentWithChildren;
import ru.sashil.admin.service.*;
import ru.sashil.admin.util.FileUtils;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import ru.sashil.admin.service.WsNotificationService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class AdminController {

    @Autowired
    private AdminDashboardService dashboardService;

    @Autowired
    private StringSimilarityService similarityService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupMemberService memberService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WsNotificationService wsNotificationService;

    // Проверка роли (вспомогательный метод)
    private boolean isAdmin(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        return user != null && user.getRole() == ru.sashil.admin.model.AdminUser.Role.ADMIN;
    }



    @GetMapping("/parents")
    public String parentsPage(HttpSession session, Model model,
                              @RequestParam(required = false) String search,
                              @RequestParam(required = false) String sortField,
                              @RequestParam(required = false) String sortOrder) {
        // Код метода parentsPage без изменений
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "parents");
        if (!isAdmin(session)) return "restricted";

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
        if (!isAdmin(session)) return "restricted";

        List<Group> allGroups = groupService.getAllGroups();

        // Создаем мапу для быстрого доступа к количеству участников по ID группы
        Map<Long, Integer> memberCounts = new HashMap<>();
        for (Group g : allGroups) {
            memberCounts.put(g.getId(), memberService.getMemberCount(g.getId()));
        }

        List<Group> filteredGroups = new ArrayList<>();
        if (search != null && !search.isEmpty()) {
            for (Group g : allGroups) {
                boolean match = false;
                String lowerSearch = search.toLowerCase();
                if (similarityService.isSimilar(g.getName(), search, 0.7)) match = true;
                if (g.getNumber().toString().contains(search)) match = true;
                if (g.getPool() != null && g.getPool().getName().toLowerCase().contains(lowerSearch)) match = true;
                if (match) filteredGroups.add(g);
            }
        } else {
            filteredGroups = allGroups;
        }

        // Сортировка (без изменений)
        if (sortField != null) {
            Comparator<Group> comparator = null;
            switch (sortField) {
                case "number": comparator = Comparator.comparing(Group::getNumber); break;
                case "name": comparator = Comparator.comparing(Group::getName, Comparator.nullsLast(String::compareToIgnoreCase)); break;
                case "pool": comparator = Comparator.comparing(g -> g.getPool() != null ? g.getPool().getName() : "", Comparator.nullsLast(String::compareToIgnoreCase)); break;
                default: comparator = Comparator.comparing(Group::getId);
            }
            if ("desc".equals(sortOrder)) comparator = comparator.reversed();
            filteredGroups.sort(comparator);
        }

        model.addAttribute("groups", filteredGroups);
        model.addAttribute("memberCounts", memberCounts); // <-- ПЕРЕДАЕМ МАПУ СЧЕТЧИКОВ
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);
        return "groups";
    }

    @GetMapping("/groups/new")
    public String newGroupPage(Model model, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("pools", groupService.getAllPools());
        model.addAttribute("coaches", groupService.getAllCoaches());
        model.addAttribute("group", new Group());
        return "new-group";
    }

    @GetMapping("/groups/edit/{id}")
    public String editGroupPage(@PathVariable Long id, Model model, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";
        Optional<Group> groupOpt = groupService.getGroupById(id);
        if (groupOpt.isEmpty()) return "redirect:/groups";
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("pools", groupService.getAllPools());
        model.addAttribute("coaches", groupService.getAllCoaches());
        model.addAttribute("group", groupOpt.get());
        model.addAttribute("isEdit", true);
        return "new-group";
    }

    @PostMapping("/groups/save")
    public String saveGroup(@ModelAttribute Group group, Model model, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/parents";
        try {
            groupService.saveGroup(group);
            wsNotificationService.sendUpdateNotification("GROUP_SAVED");
            return "redirect:/groups?success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("pools", groupService.getAllPools());
            model.addAttribute("fullName", ((AdminUser) session.getAttribute("currentUser")).getFullName());
            model.addAttribute("role", ((AdminUser) session.getAttribute("currentUser")).getRole());
            model.addAttribute("activePage", "groups");
            model.addAttribute("isEdit", group.getId() != null);
            return "new-group";
        }
    }

    @PostMapping("/groups/delete/{id}")
    public String deleteGroup(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";
        groupService.deleteGroup(id);
        wsNotificationService.sendUpdateNotification("GROUP_DELETED");
        return "redirect:/groups";
    }

    @GetMapping("/groups/{id}/members")
    public String groupMembersPage(@PathVariable Long id, Model model, HttpSession session,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) List<String> skills,
                                   @RequestParam(required = false) Integer ageFrom,
                                   @RequestParam(required = false) Integer ageTo,
                                   @RequestParam(required = false) Integer gradeFrom,
                                   @RequestParam(required = false) Integer gradeTo) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");

        Optional<ru.sashil.admin.model.Group> groupOpt = groupService.getGroupById(id);
        if (groupOpt.isEmpty()) return "redirect:/groups";

        Group group = groupOpt.get();
        int currentCount = memberService.getMemberCount(id); // <-- ПОЛУЧАЕМ ТЕКУЩЕЕ КОЛИЧЕСТВО

        model.addAttribute("group", group);
        model.addAttribute("currentCount", currentCount); // <-- ПЕРЕДАЕМ В МОДЕЛЬ
        model.addAttribute("members", memberService.getGroupMembers(id));
        model.addAttribute("availableChildren", memberService.getAvailableChildren(id, search, skills, ageFrom, ageTo, gradeFrom, gradeTo));
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSkills", skills);
        model.addAttribute("currentAgeFrom", ageFrom != null ? ageFrom : 6);
        model.addAttribute("currentAgeTo", ageTo != null ? ageTo : 18);
        model.addAttribute("currentGradeFrom", gradeFrom != null ? gradeFrom : 1);
        model.addAttribute("currentGradeTo", gradeTo != null ? gradeTo : 11);
        return "group-members";
    }

    // Логирование добавления ребенка в группу с именами
    @PostMapping("/groups/{id}/members/add")
    public String addMember(@PathVariable Long id, @RequestParam Long childId, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        // Получаем данные для лога ДО изменения
        String groupName = groupService.getGroupById(id).map(Group::getName).orElse("Группа ID=" + id);
        String childName = memberService.getChildFullName(childId); // Новый метод в сервисе

        if (user != null) {
            auditLogService.log("CHILD_ADDED_TO_GROUP", user,
                    "Ребенок \"" + childName + "\" добавлен в группу \"" + groupName + "\"");
        }

        memberService.addChildToGroup(id, childId, user);
        wsNotificationService.sendUpdateNotification("CHILD_ADDED_TO_GROUP");
        return "redirect:/groups/{id}/members";
    }

    // Логирование исключения ребенка из группы с именами
    @PostMapping("/groups/{id}/members/remove")
    public String removeMember(@PathVariable Long id, @RequestParam Long childId, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        // Получаем данные для лога ДО удаления связи
        String groupName = groupService.getGroupById(id).map(Group::getName).orElse("Группа ID=" + id);
        String childName = memberService.getChildFullName(childId); // Новый метод в сервисе

        if (user != null) {
            auditLogService.log("CHILD_REMOVED_FROM_GROUP", user,
                    "Ребенок \"" + childName + "\" исключен из группы \"" + groupName + "\"");
        }

        memberService.removeChildFromGroup(id, childId, user);
        wsNotificationService.sendUpdateNotification("CHILD_REMOVED_FROM_GROUP");
        return "redirect:/groups/{id}/members";
    }



    @GetMapping("/users")
    public String usersPage(Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        if (currentUser.getRole() != ru.sashil.admin.model.AdminUser.Role.ADMIN) return "restricted";
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "users");
        model.addAttribute("users", adminUserService.getAllUsers());
        return "users";
    }

    @GetMapping("/users/register")
    public String registerUserPage(Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        if (currentUser.getRole() != ru.sashil.admin.model.AdminUser.Role.ADMIN) return "restricted";
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "users");
        model.addAttribute("roles", AdminUser.Role.values());
        model.addAttribute("newUser", new AdminUser());
        return "register-user";
    }

    @PostMapping("/users/register")
    public String processRegister(@ModelAttribute AdminUser newUser,
                                  @RequestParam String password,
                                  HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != ru.sashil.admin.model.AdminUser.Role.ADMIN) {
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

    @GetMapping("/users/edit/{id}")
    public String editUserPage(@PathVariable Long id, Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        if (currentUser.getRole() != ru.sashil.admin.model.AdminUser.Role.ADMIN) return "restricted";
        Optional<AdminUser> userOpt = adminUserService.getUserById(id);
        if (userOpt.isEmpty()) return "redirect:/users";
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "users");
        model.addAttribute("targetUser", userOpt.get());
        return "edit-user";
    }

    @PostMapping("/users/update-password")
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

    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id,
                             @RequestParam String adminPassword,
                             HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != ru.sashil.admin.model.AdminUser.Role.ADMIN) {
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

    @GetMapping("/documents")
    public String documentsPage(Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != ru.sashil.admin.model.AdminUser.Role.ADMIN)
            return "redirect:/login";
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "documents");
        model.addAttribute("contracts", documentService.getHistory("CONTRACT"));
        model.addAttribute("consents", documentService.getHistory("CONSENT"));
        model.addAttribute("rules", documentService.getHistory("RULES"));
        model.addAttribute("receipts", documentService.getHistory("RECEIPT"));
        return "documents";
    }

    @PostMapping("/documents/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file,
                                 @RequestParam("docType") String docType,
                                 HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";
        try {
            documentService.uploadDocument(file, docType, currentUser);
            auditLogService.log("DOCUMENT_UPLOADED", currentUser,
                    "Загружен документ типа " + docType + ": " + file.getOriginalFilename());
        } catch (IOException e) {
            e.printStackTrace();
            return "redirect:/documents?error";
        }
        wsNotificationService.sendUpdateNotification("DOCUMENT_UPLOADED");
        return "redirect:/documents";
    }

    @PostMapping("/documents/activate/{id}")
    public String activateDocument(@PathVariable Long id, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        documentService.activateDocument(id);
        if (currentUser != null) {
            auditLogService.log("DOCUMENT_ACTIVATED", currentUser,
                    "Активирована версия документа ID=" + id);
        }
        wsNotificationService.sendUpdateNotification("DOCUMENT_ACTIVATED");
        return "redirect:/documents";
    }
}