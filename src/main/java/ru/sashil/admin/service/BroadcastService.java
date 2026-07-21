package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.common.util.NameUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BroadcastService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Создает задачу на рассылку.
     */
    public void createBroadcast(AdminUser sender, String targetType, Long groupId, String text) {
        String sql = "INSERT INTO pool.broadcast_messages (sender_id, target_type, target_group_id, message_text, created_at, status) VALUES (?, ?, ?, ?, ?, 'PENDING')";

        
        String finalText = text;
        if (sender.getRole() == ru.sashil.admin.model.AdminUser.Role.COACH) {
            String initials = NameUtils.toInitials(sender.getFullName());
            finalText += "\n\nС уважением, тренер " + initials;
        } else if (sender.getRole() == ru.sashil.admin.model.AdminUser.Role.ADMIN) {
            finalText += "\n\nС уважением, Администрация бассейна";
        }

        
        String recipientInfo = "";
        if ("ALL".equals(targetType)) {
            recipientInfo = "\n[Рассылка всем родителям]";
        } else if (groupId != null) {
            
            Integer groupNumber = getGroupNumberById(groupId);
            if (groupNumber != null) {
                recipientInfo = "\n[Рассылка группе №" + groupNumber + "]";
            } else {
                recipientInfo = "\n[Рассылка группе ID=" + groupId + "]"; 
            }
        }

        
        finalText = text + recipientInfo + (finalText.equals(text) ? "" : finalText.substring(text.length()));

        jdbcTemplate.update(sql, sender.getId(), targetType, groupId, finalText, LocalDateTime.now());
    }

    /**
     * Вспомогательный метод для получения номера группы по ID.
     */
    private Integer getGroupNumberById(Long groupId) {
        try {
            String sql = "SELECT number FROM pool.groups WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, Integer.class, groupId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получает историю рассылок для отображения в админке.
     */
    public List<Map<String, Object>> getBroadcastHistory() {
        String sql = "SELECT bm.id, bm.message_text, bm.created_at, bm.status, bm.sent_count, au.full_name as sender_name " +
                "FROM pool.broadcast_messages bm " +
                "LEFT JOIN pool.admin_users au ON bm.sender_id = au.id " +
                "ORDER BY bm.created_at DESC LIMIT 50";
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Получает список групп, доступных пользователю (для фильтра в UI)
     */
    public List<Map<String, Object>> getAvailableGroups(Long userId, String role) {
        if ("COACH".equals(role)) {
            return jdbcTemplate.queryForList(
                    "SELECT g.id, g.name FROM pool.groups g WHERE g.trainer_id = ?", userId
            );
        } else {
            return jdbcTemplate.queryForList("SELECT g.id, g.name FROM pool.groups g ORDER BY g.number");
        }
    }
}