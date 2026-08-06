package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.GroupJoinRequest;
import ru.sashil.admin.service.GroupJoinService;
import ru.sashil.admin.service.GroupMemberService;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/join-requests")
public class GroupJoinController {

    @Autowired private GroupJoinService joinService;
    @Autowired private GroupMemberService memberService;

    @GetMapping
    public String listRequests(Model model, HttpSession session,
                               @RequestParam(required = false) String tab) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || !"ADMIN".equals(user.getRole().name())) return "redirect:/login";

        List<GroupJoinRequest> requests;
        boolean isNewTab = !"archive".equals(tab);

        if (isNewTab) {
            requests = joinService.getRequestRepository().findByStatusOrderByCreatedAtDesc("PENDING");
        } else {
            requests = joinService.getRequestRepository().findProcessedOrderByProcessedAtDesc();
        }

        model.addAttribute("requests", requests);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "join-requests");
        model.addAttribute("currentTab", isNewTab ? "new" : "archive");

        return "group-join-requests";
    }

    @GetMapping("/{id}")
    public String viewRequest(@PathVariable Long id, Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || !"ADMIN".equals(user.getRole().name())) return "redirect:/login";

        GroupJoinRequest req = joinService.getRequestRepository().findById(id).orElseThrow();

        int memberCount = memberService.getMemberCount(req.getGroup().getId());

        // Вычисляем соответствие критериям на Java
        Map<String, Object> criteriaMatch = new HashMap<>();

        // Возраст
        boolean ageOk = true;
        if (req.getGroup().getMinAge() != null && req.getChild().getAge() < req.getGroup().getMinAge()) {
            ageOk = false;
        }
        if (req.getGroup().getMaxAge() != null && req.getChild().getAge() > req.getGroup().getMaxAge()) {
            ageOk = false;
        }
        criteriaMatch.put("ageOk", ageOk);

        // Навык
        boolean skillOk = false;
        String childSkill = req.getChild().getSkill() != null ? req.getChild().getSkill().getDbValue() : null;
        String groupSkill1 = req.getGroup().getSkill1();
        String groupSkill2 = req.getGroup().getSkill2();

        // Если у группы не заданы навыки - любой подходит
        if (groupSkill1 == null && groupSkill2 == null) {
            skillOk = true;
        } else if (childSkill != null) {
            if (childSkill.equals(groupSkill1) || childSkill.equals(groupSkill2)) {
                skillOk = true;
            }
        }
        criteriaMatch.put("skillOk", skillOk);

        // Итоговый вердикт
        criteriaMatch.put("fullyMatches", ageOk && skillOk);

        model.addAttribute("request", req);
        model.addAttribute("memberCount", memberCount);
        model.addAttribute("criteriaMatch", criteriaMatch);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "join-requests");

        return "group-join-request-detail";
    }

    @PostMapping("/{id}/process")
    public String processRequest(@PathVariable Long id,
                                 @RequestParam String status,
                                 @RequestParam(required = false) String comment,
                                 HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        joinService.processRequest(id, status, comment, user.getId());
        return "redirect:/join-requests?success=true";
    }
}