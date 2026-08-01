package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.repository.GroupJoinRequestRepository;
import ru.sashil.common.service.DatabaseService;

import java.util.HashMap;
import java.util.Map;

@RestController
public class CounterController {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private GroupJoinRequestRepository joinRequestRepository;

    @GetMapping("/counters/update")
    public Map<String, Object> getCounters(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) {
            result.put("unreadCertsCount", 0);
            result.put("unreadJoinRequestsCount", 0);
            return result;
        }

        try {
            // Справки
            int certsCount = 0;
            if (user.getRole() == AdminUser.Role.COACH) {
                certsCount = databaseService.getUnreadCertificatesForCoach(user.getId()).size();
            } else if (user.getRole() == AdminUser.Role.ADMIN) {
                certsCount = databaseService.getUnreadCertificates().size();
            }
            result.put("unreadCertsCount", certsCount);

            // Заявки
            int joinCount = 0;
            if (user.getRole() == AdminUser.Role.ADMIN) {
                joinCount = joinRequestRepository.countByStatus("PENDING");
            }
            result.put("unreadJoinRequestsCount", joinCount);

        } catch (Exception e) {
            result.put("unreadCertsCount", 0);
            result.put("unreadJoinRequestsCount", 0);
        }

        return result;
    }
}