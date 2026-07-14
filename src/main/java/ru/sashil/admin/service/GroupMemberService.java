package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.GroupChild;
import ru.sashil.admin.model.GroupChildId;
import ru.sashil.admin.repository.GroupChildRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GroupMemberService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupChildRepository groupChildRepository;

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

    public void addChildToGroup(Long groupId, Long childId, AdminUser actor) {
        if (!groupChildRepository.existsById(new GroupChildId(groupId, childId))) {
            GroupChild link = new GroupChild();
            link.setGroupId(groupId);
            link.setChildId(childId);
            link.setCreatedAt(LocalDateTime.now());
            groupChildRepository.save(link);
        }
    }

    @Transactional
    public void removeChildFromGroup(Long groupId, Long childId, AdminUser actor) {
        groupChildRepository.deleteByGroupIdAndChildId(groupId, childId);
    }
}