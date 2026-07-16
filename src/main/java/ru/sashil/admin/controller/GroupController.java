package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.service.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@Controller
@RequestMapping("/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupMemberService memberService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WsNotificationService wsNotificationService;

    private boolean isAdmin(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        return user != null && user.getRole() == ru.sashil.admin.model.AdminUser.Role.ADMIN;
    }

    @GetMapping
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
        Map<Long, Integer> memberCounts = new HashMap<>();
        for (Group g : allGroups) {
            memberCounts.put(g.getId(), memberService.getMemberCount(g.getId()));
        }

        List<Group> filteredGroups = new ArrayList<>();
        if (search != null && !search.isEmpty()) {
            for (Group g : allGroups) {
                boolean match = false;
                String lowerSearch = search.toLowerCase();
                if (g.getNumber().toString().contains(search)) match = true;
                if (g.getName().toLowerCase().contains(lowerSearch)) match = true;
                if (g.getPool() != null && g.getPool().getName().toLowerCase().contains(lowerSearch)) match = true;
                if (match) filteredGroups.add(g);
            }
        } else {
            filteredGroups = allGroups;
        }

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
        model.addAttribute("memberCounts", memberCounts);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortOrder", sortOrder);

        return "groups";
    }

    @GetMapping("/new")
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

    @GetMapping("/edit/{id}")
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

    @PostMapping("/save")
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

    @PostMapping("/delete/{id}")
    public String deleteGroup(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        groupService.deleteGroup(id);
        wsNotificationService.sendUpdateNotification("GROUP_DELETED");
        return "redirect:/groups";
    }

    @GetMapping("/{id}/members")
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

        Optional<Group> groupOpt = groupService.getGroupById(id);
        if (groupOpt.isEmpty()) return "redirect:/groups";

        Group group = groupOpt.get();
        int currentCount = memberService.getMemberCount(id);

        model.addAttribute("group", group);
        model.addAttribute("currentCount", currentCount);
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

    @PostMapping("/{id}/members/add")
    public String addMember(@PathVariable Long id, @RequestParam Long childId, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        String groupName = groupService.getGroupById(id).map(Group::getName).orElse("Группа ID=" + id);
        String childName = memberService.getChildFullName(childId);

        if (user != null) {
            auditLogService.log("CHILD_ADDED_TO_GROUP", user,
                    "Ребенок \"" + childName + "\" добавлен в группу \"" + groupName + "\"");
        }

        memberService.addChildToGroup(id, childId, user);
        wsNotificationService.sendUpdateNotification("CHILD_ADDED_TO_GROUP");
        return "redirect:/groups/{id}/members";
    }

    @PostMapping("/{id}/members/remove")
    public String removeMember(@PathVariable Long id, @RequestParam Long childId, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        String groupName = groupService.getGroupById(id).map(Group::getName).orElse("Группа ID=" + id);
        String childName = memberService.getChildFullName(childId);

        if (user != null) {
            auditLogService.log("CHILD_REMOVED_FROM_GROUP", user,
                    "Ребенок \"" + childName + "\" исключен из группы \"" + groupName + "\"");
        }

        memberService.removeChildFromGroup(id, childId, user);
        wsNotificationService.sendUpdateNotification("CHILD_REMOVED_FROM_GROUP");
        return "redirect:/groups/{id}/members";
    }


    @GetMapping("/transfer")
    public String transferPage(Model model, HttpSession session,
                               @RequestParam(required = false) Long group1Id,
                               @RequestParam(required = false) Long group2Id) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");

        // Получаем все группы для выбора в выпадающих списках
        List<Group> allGroups = groupService.getAllGroups();
        model.addAttribute("allGroups", allGroups);

        // Если группы выбраны, загружаем их участников
        if (group1Id != null) {
            Optional<Group> g1 = groupService.getGroupById(group1Id);
            if (g1.isPresent()) {
                model.addAttribute("group1", g1.get());
                model.addAttribute("members1", memberService.getGroupMembers(group1Id));
            }
        }

        if (group2Id != null) {
            Optional<Group> g2 = groupService.getGroupById(group2Id);
            if (g2.isPresent()) {
                model.addAttribute("group2", g2.get());
                model.addAttribute("members2", memberService.getGroupMembers(group2Id));
            }
        }

        return "group-transfer";
    }

    @PostMapping("/transfer/save")
    public String saveTransfer(@RequestParam Long group1Id,
                               @RequestParam Long group2Id,
                               @RequestParam(required = false) List<Long> toGroup2, // ID детей, которых перемещаем из 1 во 2
                               @RequestParam(required = false) List<Long> toGroup1, // ID детей, которых перемещаем из 2 в 1
                               HttpSession session) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        // Перемещение из Группы 1 в Группу 2
        if (toGroup2 != null) {
            for (Long childId : toGroup2) {
                // Сначала удаляем из старой группы
                memberService.removeChildFromGroup(group1Id, childId, user);
                // Добавляем в новую (с новой датой created_at, так как это изменение)
                memberService.addChildToGroup(group2Id, childId, user);

                auditLogService.log("CHILD_TRANSFERRED", user,
                        "Ребенок ID=" + childId + " перемещен из группы ID=" + group1Id + " в группу ID=" + group2Id);
            }
        }

        // Перемещение из Группы 2 в Группу 1
        if (toGroup1 != null) {
            for (Long childId : toGroup1) {
                memberService.removeChildFromGroup(group2Id, childId, user);
                memberService.addChildToGroup(group1Id, childId, user);

                auditLogService.log("CHILD_TRANSFERRED", user,
                        "Ребенок ID=" + childId + " перемещен из группы ID=" + group2Id + " в группу ID=" + group1Id);
            }
        }

        wsNotificationService.sendUpdateNotification("GROUP_MEMBERS_TRANSFERRED");
        return "redirect:/groups/transfer?group1Id=" + group1Id + "&group2Id=" + group2Id + "&success=true";
    }


}