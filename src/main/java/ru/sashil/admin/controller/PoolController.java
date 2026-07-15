package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.service.WsNotificationService;
import ru.sashil.admin.service.PoolService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/pools")
public class PoolController {

    @Autowired
    private PoolService poolService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WsNotificationService wsNotificationService;

    private boolean isAdmin(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        return user != null && user.getRole() == ru.sashil.admin.model.AdminUser.Role.ADMIN;
    }

    @GetMapping
    public String poolsPage(Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || !isAdmin(session)) return "redirect:/login";

        List<Pool> pools = poolService.getAllPools();
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "pools");
        model.addAttribute("pools", pools);
        return "pools";
    }

    // Страница добавления нового бассейна
    @GetMapping("/new")
    public String newPoolPage(Model model, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/login";
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "pools");
        model.addAttribute("pool", new Pool());
        model.addAttribute("isEdit", false);
        return "edit-pool";
    }

    // Страница редактирования существующего бассейна
    @GetMapping("/edit/{id}")
    public String editPoolPage(@PathVariable Long id, Model model, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/login";
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");

        Optional<Pool> poolOpt = poolService.getPoolById(id);
        if (poolOpt.isEmpty()) return "redirect:/pools";

        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "pools");
        model.addAttribute("pool", poolOpt.get());
        model.addAttribute("isEdit", true);
        return "edit-pool";
    }

    @PostMapping("/save")
    public String savePool(@ModelAttribute Pool pool,
                           @RequestParam String adminPassword,
                           HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || !isAdmin(session)) return "redirect:/login";

        if (!passwordEncoder.matches(adminPassword, currentUser.getPasswordHash())) {
            return "redirect:/pools?error=wrong_password";
        }

        poolService.savePool(pool);
        wsNotificationService.sendUpdateNotification("POOL_SAVED");
        return "redirect:/pools";
    }

    @PostMapping("/delete/{id}")
    public String deletePool(@PathVariable Long id,
                             @RequestParam String adminPassword,
                             HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || !isAdmin(session)) return "redirect:/login";

        if (!passwordEncoder.matches(adminPassword, currentUser.getPasswordHash())) {
            return "redirect:/pools?error=wrong_password";
        }

        poolService.deletePool(id);
        wsNotificationService.sendUpdateNotification("POOL_DELETED");
        return "redirect:/pools";
    }
}