package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.AuditLogService;
import ru.sashil.admin.service.WsNotificationService;

import java.util.Map;

@Controller
@RequestMapping("/children/certificate")
public class CertificateReceivedController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @PostMapping("/toggle/{childId}")
    public String toggleCertificate(@PathVariable Long childId,
                                    @RequestParam(required = false) String action,
                                    HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        // Только ADMIN и COACH могут управлять отметкой
        if (user.getRole() != AdminUser.Role.ADMIN && user.getRole() != AdminUser.Role.COACH) {
            return "redirect:/children/profile/" + childId + "?error=access_denied";
        }

        try {
            // Получаем текущее значение
            String selectSql = "SELECT certificate_received FROM pool.children WHERE id = ?";
            Boolean current = jdbcTemplate.queryForObject(selectSql, Boolean.class, childId);

            boolean newValue = !current;

            // Обновляем
            String updateSql = "UPDATE pool.children SET certificate_received = ? WHERE id = ?";
            jdbcTemplate.update(updateSql, newValue, childId);

            // Получаем имя ребенка для лога
            String nameSql = "SELECT first_name, last_name FROM pool.children WHERE id = ?";
            Map<String, Object> child = jdbcTemplate.queryForMap(nameSql, childId);
            String childName = child.get("last_name") + " " + child.get("first_name");

            // Логируем
            auditLogService.log("CERTIFICATE_RECEIVED_TOGGLED", user,
                    (newValue ? "Отметка о получении справки установлена" : "Отметка о получении справки снята") +
                            " для ребенка \"" + childName + "\" (ID=" + childId + ")");

            wsNotificationService.sendUpdateNotification("CERTIFICATE_RECEIVED_TOGGLED");

            return "redirect:/children/profile/" + childId + "?success=certificate_toggled";
        } catch (Exception e) {
            return "redirect:/children/profile/" + childId + "?error=certificate_toggle_failed";
        }
    }
}