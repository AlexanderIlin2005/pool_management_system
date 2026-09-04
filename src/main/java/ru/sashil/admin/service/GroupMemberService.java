package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.GroupChild;
import ru.sashil.admin.model.GroupChildId;
import ru.sashil.admin.repository.GroupChildRepository;
import ru.sashil.common.service.DatabaseService;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GroupMemberService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupChildRepository groupChildRepository;

    @Autowired
    private DatabaseService databaseService;

    public List<Map<String, Object>> getGroupMembers(Long groupId) {
        String sql = "SELECT c.id, c.first_name, c.last_name, c.age, c.grade_number, c.grade_name, c.skill FROM pool.children c " +
                "JOIN pool.group_children gc ON c.id = gc.child_id WHERE gc.group_id = ? ORDER BY c.last_name";
        return jdbcTemplate.queryForList(sql, groupId);
    }

    /**
     * Возвращает текущее количество участников в группе.
     */
    public int getMemberCount(Long groupId) {
        String sql = "SELECT COUNT(*) FROM pool.group_children WHERE group_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, groupId);
    }

    public List<Map<String, Object>> getAvailableChildren(Long groupId, String search,
                                                          List<String> skills, Integer ageFrom, Integer ageTo,
                                                          Integer gradeFrom, Integer gradeTo) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.first_name, c.last_name, c.age, c.grade_number, c.grade_name, c.skill FROM pool.children c " +
                        "WHERE c.id NOT IN (SELECT child_id FROM pool.group_children WHERE group_id = ?)"
        );
        List<Object> params = new ArrayList<>();
        params.add(groupId);

        if (ageFrom != null) { sql.append(" AND c.age >= ?"); params.add(ageFrom); }
        if (ageTo != null) { sql.append(" AND c.age <= ?"); params.add(ageTo); }
        if (gradeFrom != null) { sql.append(" AND c.grade_number >= ?"); params.add(gradeFrom); }
        if (gradeTo != null) { sql.append(" AND c.grade_number <= ?"); params.add(gradeTo); }

        if (skills != null && !skills.isEmpty()) {
            sql.append(" AND c.skill IN (");
            for (int i = 0; i < skills.size(); i++) {
                sql.append("?::pool.swimming_skill");
                if (i < skills.size() - 1) sql.append(",");
                params.add(skills.get(i));
            }
            sql.append(")");
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (c.first_name ILIKE ? OR c.last_name ILIKE ?)");
            String likeSearch = "%" + search + "%";
            params.add(likeSearch);
            params.add(likeSearch);
        }

        sql.append(" ORDER BY c.last_name, c.first_name");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public String getChildFullName(Long childId) {
        String sql = "SELECT first_name, last_name FROM pool.children WHERE id = ?";
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, childId);
            String firstName = (String) row.get("first_name");
            String lastName = (String) row.get("last_name");
            return (lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "");
        } catch (Exception e) {
            return "Ребенок ID=" + childId;
        }
    }

    @Transactional
    public void addChildToGroup(Long groupId, Long childId, AdminUser actor) {
        if (!groupChildRepository.existsById(new GroupChildId(groupId, childId))) {
            GroupChild link = new GroupChild();
            link.setGroupId(groupId);
            link.setChildId(childId);
            link.setCreatedAt(LocalDateTime.now());
            groupChildRepository.save(link);

            // ===== ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ РОДИТЕЛЮ =====
            sendMemberNotification(groupId, childId, actor, "ADDED");
        }
    }

    @Transactional
    public void removeChildFromGroup(Long groupId, Long childId, AdminUser actor) {
        boolean existed = groupChildRepository.existsById(new GroupChildId(groupId, childId));

        groupChildRepository.deleteByGroupIdAndChildId(groupId, childId);

        if (existed) {
            sendMemberNotification(groupId, childId, actor, "REMOVED");
        }
    }

    /**
     * Отправляет уведомление родителю о добавлении/удалении ребенка из группы
     */
    private void sendMemberNotification(Long groupId, Long childId, AdminUser actor, String type) {
        try {
            // Получаем данные о ребенке
            String childSql = "SELECT c.first_name, c.last_name, c.parent_id, p.vk_id as parent_vk_id " +
                    "FROM pool.children c " +
                    "JOIN pool.parents p ON c.parent_id = p.id " +
                    "WHERE c.id = ?";
            Map<String, Object> childData = jdbcTemplate.queryForMap(childSql, childId);

            Long parentVkId = (Long) childData.get("parent_vk_id");
            if (parentVkId == null) {
                return; // У родителя нет VK ID
            }

            // ===== ПОЛУЧАЕМ КОММЕНТАРИЙ ИЗ ЗАЯВКИ (если она была) =====
            String adminComment = null;
            String joinRequestSql =
                    "SELECT admin_comment, status FROM pool.group_join_requests " +
                            "WHERE child_id = ? AND group_id = ? AND status = 'APPROVED' " +
                            "ORDER BY processed_at DESC LIMIT 1";

            try {
                Map<String, Object> joinRequest = jdbcTemplate.queryForMap(joinRequestSql, childId, groupId);
                String status = (String) joinRequest.get("status");
                if ("APPROVED".equals(status)) {
                    adminComment = (String) joinRequest.get("admin_comment");
                }
            } catch (Exception e) {
                // Нет заявки или ошибка — просто игнорируем
            }

            // Дополнительная проверка: есть ли уже неотправленное уведомление в group_member_notifications
            String notificationType = "ADDED".equals(type) ? "ADDED" : "REMOVED";
            boolean alreadyNotified = databaseService.hasPendingGroupMemberNotification(
                    parentVkId, childId, groupId, notificationType
            );

            if (alreadyNotified) {
                return; // Уже есть уведомление
            }

            // ===== ФОРМИРУЕМ УВЕДОМЛЕНИЕ =====
            // Получаем данные о группе и расписании
            String groupInfoSql = "SELECT g.name, " +
                    "g.day_1_start, g.day_1_end, " +
                    "g.day_2_start, g.day_2_end, " +
                    "g.day_3_start, g.day_3_end, " +
                    "g.day_4_start, g.day_4_end, " +
                    "g.day_5_start, g.day_5_end, " +
                    "g.day_6_start, g.day_6_end, " +
                    "g.day_7_start, g.day_7_end, " +
                    "au.full_name as trainer_name " +
                    "FROM pool.groups g " +
                    "LEFT JOIN pool.admin_users au ON g.trainer_id = au.id " +
                    "WHERE g.id = ?";
            Map<String, Object> groupData = jdbcTemplate.queryForMap(groupInfoSql, groupId);

            String groupName = (String) groupData.get("name");
            String trainerName = (String) groupData.get("trainer_name");

            // Форматируем расписание
            String schedule = formatSchedule(groupData);

            // Форматируем имя тренера
            String trainerDisplay = "не назначен";
            if (trainerName != null && !trainerName.trim().isEmpty()) {
                trainerDisplay = ru.sashil.common.util.NameUtils.toInitials(trainerName);
            }

            String childName = childData.get("last_name") + " " + childData.get("first_name");

            String message;
            if ("ADDED".equals(type)) {
                message = "✅ Ребенок " + childName + " зачислен в группу \"" + groupName + "\".\n\n" +
                        "Расписание занятий:\n" +
                        schedule + "\n\n" +
                        "Тренер: " + trainerDisplay + "\n" +
                        "Группа: " + groupName;

                // Добавляем комментарий администратора, если он есть
                if (adminComment != null && !adminComment.trim().isEmpty()) {
                    message += "\n\nКомментарий администратора: " + adminComment;
                }

                message += "\n\nПожалуйста, запомните эти данные.";
            } else {
                message = "❌ Ребенок " + childName + " исключен из группы \"" + groupName + "\" администратором.";
            }

            // Сохраняем уведомление в БД
            databaseService.saveGroupMemberNotification(
                    parentVkId, childId, groupId,
                    "ADDED".equals(type) ? "ADDED" : "REMOVED",
                    message
            );

        } catch (Exception e) {
            System.err.println("Ошибка отправки уведомления о членстве в группе: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Форматирует расписание группы в читаемый вид
     */
    private String formatSchedule(Map<String, Object> groupData) {
        String[] dayNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        String[] startFields = {"day_1_start", "day_2_start", "day_3_start", "day_4_start",
                "day_5_start", "day_6_start", "day_7_start"};
        String[] endFields = {"day_1_end", "day_2_end", "day_3_end", "day_4_end",
                "day_5_end", "day_6_end", "day_7_end"};

        StringBuilder sb = new StringBuilder();
        boolean hasSchedule = false;

        for (int i = 0; i < 7; i++) {
            java.sql.Time start = (java.sql.Time) groupData.get(startFields[i]);
            java.sql.Time end = (java.sql.Time) groupData.get(endFields[i]);

            if (start != null && end != null) {
                if (hasSchedule) sb.append("\n");
                String startStr = start.toString().substring(0, 5);
                String endStr = end.toString().substring(0, 5);
                sb.append(dayNames[i] + " " + startStr + " - " + endStr);
                hasSchedule = true;
            }
        }

        if (!hasSchedule) {
            sb.append("Расписание не указано");
        }

        return sb.toString();
    }
}