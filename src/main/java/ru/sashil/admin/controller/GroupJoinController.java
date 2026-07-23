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

@Controller
@RequestMapping("/join-requests")
public class GroupJoinController {

    @Autowired private GroupJoinService joinService;
    @Autowired private GroupMemberService memberService;

    @GetMapping
    public String listRequests(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || !"ADMIN".equals(user.getRole().name())) return "redirect:/login";

        List<GroupJoinRequest> requests = joinService.getRequestRepository().findByStatusOrderByCreatedAtDesc("PENDING");
        model.addAttribute("requests", requests);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "join-requests");
        return "group-join-requests";
    }

    @GetMapping("/{id}")
    public String viewRequest(@PathVariable Long id, Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null || !"ADMIN".equals(user.getRole().name())) return "redirect:/login";

        GroupJoinRequest req = joinService.getRequestRepository().findById(id).orElseThrow();

        // Получаем количество участников в группе
        int memberCount = memberService.getMemberCount(req.getGroup().getId());

        model.addAttribute("request", req);
        model.addAttribute("memberCount", memberCount);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "groups");
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