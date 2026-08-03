package ru.sashil.admin.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.repository.GroupJoinRequestRepository;
import ru.sashil.admin.repository.MessageRepository;
import ru.sashil.common.service.DatabaseService;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private GroupJoinRequestRepository joinRequestRepository;

    @Autowired
    private MessageRepository messageRepository;

    @ModelAttribute("unreadCertsCount")
    public Integer getUnreadCertsCount(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return 0;

        try {
            if (user.getRole() == AdminUser.Role.COACH) {
                return databaseService.getUnreadCertificatesForCoach(user.getId()).size();
            } else if (user.getRole() == AdminUser.Role.ADMIN) {
                return databaseService.getUnreadCertificates().size();
            }
        } catch (Exception e) {
            // Игнорируем ошибки, просто возвращаем 0
        }
        return 0;
    }

    @ModelAttribute("unreadJoinRequestsCount")
    public Integer getUnreadJoinRequestsCount(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return 0;

        try {
            if (user.getRole() == AdminUser.Role.ADMIN) {
                return joinRequestRepository.countByStatus("PENDING");
            }
        } catch (Exception e) {
            // Игнорируем ошибки, просто возвращаем 0
        }
        return 0;
    }

    @ModelAttribute("unreadMessagesCount")
    public Integer getUnreadMessagesCount(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return 0;

        try {
            if (user.getRole() == AdminUser.Role.ADMIN) {
                return messageRepository.findPendingForAdmins().size();
            } else if (user.getRole() == AdminUser.Role.COACH) {
                return messageRepository.findPendingForCoach(user.getId()).size();
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return 0;
    }
}