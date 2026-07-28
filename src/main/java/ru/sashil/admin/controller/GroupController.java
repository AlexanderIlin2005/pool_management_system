package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.*;
import ru.sashil.admin.service.*;
import ru.sashil.admin.repository.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
    private ScheduleService scheduleService;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @Autowired
    private GroupRepository groupRepository;

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

        List<Group> allGroups;

        if (user.getRole() == AdminUser.Role.ADMIN) {
            allGroups = groupService.getAllGroups();
        } else if (user.getRole() == AdminUser.Role.COACH) {
            allGroups = groupRepository.findByTrainer_Id(user.getId());
        } else {
            return "restricted";
        }

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

        Group group = groupOpt.get();

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("pools", groupService.getAllPools());
        model.addAttribute("coaches", groupService.getAllCoaches());
        model.addAttribute("group", group);
        model.addAttribute("isEdit", true);

        // Передаем выбранные навыки для чекбоксов
        if (group.getSkill1() != null) model.addAttribute("selectedSkill1", group.getSkill1());
        if (group.getSkill2() != null) model.addAttribute("selectedSkill2", group.getSkill2());

        return "new-group";
    }

    @PostMapping("/save")
    public String saveGroup(@ModelAttribute Group group,
                            @RequestParam(required = false) String skill1,
                            @RequestParam(required = false) String skill2,
                            @RequestParam(required = false) String skill3,
                            Model model, HttpSession session) {

        if (!isAdmin(session)) return "redirect:/parents";

        try {
            // Собираем выбранные навыки в поля модели
            List<String> selectedSkills = new ArrayList<>();
            if ("не умеет".equals(skill1)) selectedSkills.add("не умеет");
            if ("держится на воде".equals(skill2)) selectedSkills.add("держится на воде");
            if ("уверенно плавает".equals(skill3)) selectedSkills.add("уверенно плавает");

            if (selectedSkills.size() > 0) group.setSkill1(selectedSkills.get(0));
            if (selectedSkills.size() > 1) group.setSkill2(selectedSkills.get(1));

            groupService.saveGroup(group);
            wsNotificationService.sendUpdateNotification("GROUP_SAVED");
            return "redirect:/groups?success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("pools", groupService.getAllPools());
            model.addAttribute("coaches", groupService.getAllCoaches());
            model.addAttribute("fullName", ((AdminUser) session.getAttribute("currentUser")).getFullName());
            model.addAttribute("role", ((AdminUser) session.getAttribute("currentUser")).getRole());
            model.addAttribute("activePage", "groups");
            model.addAttribute("isEdit", group.getId() != null);
            // Возвращаем выбранные навыки обратно в форму, чтобы они не сбросились
            if (group.getSkill1() != null) model.addAttribute("selectedSkill1", group.getSkill1());
            if (group.getSkill2() != null) model.addAttribute("selectedSkill2", group.getSkill2());
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
                                   @RequestParam(required = false) Integer gradeTo,
                                   HttpServletRequest request) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("currentUri", request.getRequestURI());

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

    // НОВЫЙ МЕТОД ДЛЯ AJAX ФИЛЬТРАЦИИ
    @GetMapping("/{id}/members/filter")
    public String filterMembers(@PathVariable Long id,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) List<String> skills,
                                @RequestParam(required = false) Integer ageFrom,
                                @RequestParam(required = false) Integer ageTo,
                                @RequestParam(required = false) Integer gradeFrom,
                                @RequestParam(required = false) Integer gradeTo,
                                Model model, HttpSession session) {

        Optional<Group> groupOpt = groupService.getGroupById(id);
        if (groupOpt.isEmpty()) return "fragments/member-table :: tableContent";

        Group group = groupOpt.get();

        model.addAttribute("group", group);
        model.addAttribute("availableChildren", memberService.getAvailableChildren(id, search, skills, ageFrom, ageTo, gradeFrom, gradeTo));
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSkills", skills);
        model.addAttribute("currentAgeFrom", ageFrom != null ? ageFrom : 6);
        model.addAttribute("currentAgeTo", ageTo != null ? ageTo : 18);
        model.addAttribute("currentGradeFrom", gradeFrom != null ? gradeFrom : 1);
        model.addAttribute("currentGradeTo", gradeTo != null ? gradeTo : 11);

        return "fragments/member-table :: tableContent";
    }


    @PostMapping("/{id}/members/add")
    public String addMember(@PathVariable Long id,
                            @RequestParam Long childId,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) List<String> skills,
                            @RequestParam(required = false) Integer ageFrom,
                            @RequestParam(required = false) Integer ageTo,
                            @RequestParam(required = false) Integer gradeFrom,
                            @RequestParam(required = false) Integer gradeTo,
                            HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        String groupName = groupService.getGroupById(id).map(Group::getName).orElse("Группа ID=" + id);
        String childName = memberService.getChildFullName(childId);

        if (user != null) {
            auditLogService.log("CHILD_ADDED_TO_GROUP", user,
                    "Ребенок \"" + childName + "\" добавлен в группу \"" + groupName + "\"");
        }

        memberService.addChildToGroup(id, childId, user);
        wsNotificationService.sendUpdateNotification("CHILD_ADDED_TO_GROUP");

        // Формируем URL с сохранением фильтров
        StringBuilder redirectUrl = new StringBuilder("redirect:/groups/" + id + "/members");
        boolean hasParams = false;

        if (search != null && !search.isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("search=").append(search);
            hasParams = true;
        }
        if (ageFrom != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("ageFrom=").append(ageFrom);
            hasParams = true;
        }
        if (ageTo != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("ageTo=").append(ageTo);
            hasParams = true;
        }
        if (gradeFrom != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("gradeFrom=").append(gradeFrom);
            hasParams = true;
        }
        if (gradeTo != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("gradeTo=").append(gradeTo);
            hasParams = true;
        }
        if (skills != null) {
            for (String skill : skills) {
                redirectUrl.append(hasParams ? "&" : "?").append("skills=").append(skill);
                hasParams = true;
            }
        }

        return redirectUrl.toString();
    }

    @PostMapping("/{id}/members/remove")
    public String removeMember(@PathVariable Long id,
                               @RequestParam Long childId,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) List<String> skills,
                               @RequestParam(required = false) Integer ageFrom,
                               @RequestParam(required = false) Integer ageTo,
                               @RequestParam(required = false) Integer gradeFrom,
                               @RequestParam(required = false) Integer gradeTo,
                               HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");

        String groupName = groupService.getGroupById(id).map(Group::getName).orElse("Группа ID=" + id);
        String childName = memberService.getChildFullName(childId);

        if (user != null) {
            auditLogService.log("CHILD_REMOVED_FROM_GROUP", user,
                    "Ребенок \"" + childName + "\" исключен из группы \"" + groupName + "\"");
        }

        memberService.removeChildFromGroup(id, childId, user);
        wsNotificationService.sendUpdateNotification("CHILD_REMOVED_FROM_GROUP");

        // Формируем URL с сохранением фильтров
        StringBuilder redirectUrl = new StringBuilder("redirect:/groups/" + id + "/members");
        boolean hasParams = false;

        if (search != null && !search.isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("search=").append(search);
            hasParams = true;
        }
        if (ageFrom != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("ageFrom=").append(ageFrom);
            hasParams = true;
        }
        if (ageTo != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("ageTo=").append(ageTo);
            hasParams = true;
        }
        if (gradeFrom != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("gradeFrom=").append(gradeFrom);
            hasParams = true;
        }
        if (gradeTo != null) {
            redirectUrl.append(hasParams ? "&" : "?").append("gradeTo=").append(gradeTo);
            hasParams = true;
        }
        if (skills != null) {
            for (String skill : skills) {
                redirectUrl.append(hasParams ? "&" : "?").append("skills=").append(skill);
                hasParams = true;
            }
        }

        return redirectUrl.toString();
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


        List<Group> allGroups = groupService.getAllGroups();
        model.addAttribute("allGroups", allGroups);


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
                               @RequestParam(required = false) List<Long> toGroup2,
                               @RequestParam(required = false) List<Long> toGroup1,
                               HttpSession session) {
        if (!isAdmin(session)) return "redirect:/parents";
        AdminUser user = (AdminUser) session.getAttribute("currentUser");


        if (toGroup2 != null) {
            for (Long childId : toGroup2) {
                memberService.removeChildFromGroup(group1Id, childId, user);
                memberService.addChildToGroup(group2Id, childId, user);
                auditLogService.log("CHILD_TRANSFERRED", user,
                        "Ребенок ID=" + childId + " перемещен из группы ID=" + group1Id + " в группу ID=" + group2Id);
            }
        }


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


    @GetMapping("/{id}/attendance")
    public String groupAttendancePage(@PathVariable Long id, Model model, HttpSession session,
                                      @RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) Integer month) {

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Optional<Group> groupOpt = groupService.getGroupById(id);
        if (groupOpt.isEmpty()) return "redirect:/groups";

        Group group = groupOpt.get();


        if (user.getRole() == AdminUser.Role.COACH) {
            if (group.getTrainer() == null || !group.getTrainer().getId().equals(user.getId())) {
                return "redirect:/schedule?error=access_denied";
            }
        } else if (user.getRole() != AdminUser.Role.ADMIN) {
            return "restricted";
        }

        LocalDate today = LocalDate.now();
        int currentYear = year != null ? year : today.getYear();
        int currentMonth = month != null ? month : today.getMonthValue();


        Map<String, Object> attendanceData = scheduleService.getMonthlyAttendance(group, currentYear, currentMonth);

        List<LocalDate> days = (List<LocalDate>) attendanceData.get("days");
        List<ChildSimple> children = (List<ChildSimple>) attendanceData.get("children");
        Map<Long, Map<LocalDate, Attendance.Status>> rawAttendanceMap =
                (Map<Long, Map<LocalDate, Attendance.Status>>) attendanceData.get("attendanceMap");


        List<Holiday> holidays = calendarService.getAllHolidays();
        List<SchoolVacation> vacations = calendarService.getAllVacations();

        Set<LocalDate> holidayDates = holidays.stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());
        Set<LocalDate> vacationDates = new HashSet<>();
        for (SchoolVacation v : vacations) {
            LocalDate d = v.getStartDate();
            while (!d.isAfter(v.getEndDate())) {
                vacationDates.add(d);
                d = d.plusDays(1);
            }
        }


        List<Map<String, Object>> tableRows = new ArrayList<>();

        if (children != null) {
            for (ChildSimple child : children) {
                Map<String, Object> row = new HashMap<>();


                String middleName = child.getMiddleName() != null ? " " + child.getMiddleName() : "";
                row.put("fullName", child.getLastName() + " " + child.getFirstName() + middleName);


                List<String> statusSymbols = new ArrayList<>();
                Map<LocalDate, Attendance.Status> childMap = rawAttendanceMap.getOrDefault(child.getId(), Collections.emptyMap());

                for (LocalDate day : days) {
                    Attendance.Status status = childMap.get(day);
                    if (status != null) {
                        statusSymbols.add(status.getLabel().substring(0, 1));
                    } else {
                        statusSymbols.add("");
                    }
                }

                row.put("statusSymbols", statusSymbols);
                tableRows.add(row);
            }
        }

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("group", group);
        model.addAttribute("year", currentYear);
        model.addAttribute("month", currentMonth);
        model.addAttribute("days", days);
        model.addAttribute("tableRows", tableRows);

        model.addAttribute("holidayDates", holidayDates);
        model.addAttribute("vacationDates", vacationDates);


        model.addAttribute("prevMonthDate", LocalDate.of(currentYear, currentMonth, 1).minusMonths(1));
        model.addAttribute("nextMonthDate", LocalDate.of(currentYear, currentMonth, 1).plusMonths(1));


        LocalDate minNavigationDate = null;
        if (group.getCreatedAt() != null) {
            minNavigationDate = group.getCreatedAt().toLocalDate();
        }
        model.addAttribute("minNavigationDate", minNavigationDate);

        return "group-attendance";
    }


    @GetMapping("/{id}/members-view")
    public String viewMembers(@PathVariable Long id, Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Optional<Group> groupOpt = groupService.getGroupById(id);
        if (groupOpt.isEmpty()) return "redirect:/groups";

        Group group = groupOpt.get();
        int currentCount = memberService.getMemberCount(id);

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
        model.addAttribute("group", group);
        model.addAttribute("currentCount", currentCount);
        model.addAttribute("members", memberService.getGroupMembers(id));

        return "group-members-view";
    }



}