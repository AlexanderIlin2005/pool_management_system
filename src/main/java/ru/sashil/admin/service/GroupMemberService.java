package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.GroupChild;
import ru.sashil.admin.model.GroupChildId;
import ru.sashil.admin.repository.GroupChildRepository;

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

    public List<Map<String, Object>> getAvailableChildren(Long groupId, String search,
                                                          List<String> skills, Integer ageFrom, Integer ageTo,
                                                          Integer gradeFrom, Integer gradeTo) {

        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.first_name, c.last_name, c.age, c.grade_number, c.grade_name, c.skill FROM pool.children c " +
                        "WHERE c.id NOT IN (SELECT child_id FROM pool.group_children WHERE group_id = ?)"
        );

        List<Object> params = new ArrayList<>();
        params.add(groupId);

        // Фильтр по возрасту
        if (ageFrom != null) {
            sql.append(" AND c.age >= ?");
            params.add(ageFrom);
        }
        if (ageTo != null) {
            sql.append(" AND c.age <= ?");
            params.add(ageTo);
        }

        // Фильтр по классу
        if (gradeFrom != null) {
            sql.append(" AND c.grade_number >= ?");
            params.add(gradeFrom);
        }
        if (gradeTo != null) {
            sql.append(" AND c.grade_number <= ?");
            params.add(gradeTo);
        }

        // Фильтр по навыкам (исправляем ошибку приведения типов)
        if (skills != null && !skills.isEmpty()) {
            sql.append(" AND c.skill IN (");
            for (int i = 0; i < skills.size(); i++) {
                // ВАЖНО: ::pool.swimming_skill приводит строку к типу ENUM
                sql.append("?::pool.swimming_skill");
                if (i < skills.size() - 1) sql.append(",");
                params.add(skills.get(i));
            }
            sql.append(")");
        }

        // Поиск по ФИО
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (c.first_name ILIKE ? OR c.last_name ILIKE ?)");
            String likeSearch = "%" + search + "%";
            params.add(likeSearch);
            params.add(likeSearch);
        }

        sql.append(" ORDER BY c.last_name, c.first_name");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public void addChildToGroup(Long groupId, Long childId) {
        if (!groupChildRepository.existsById(new GroupChildId(groupId, childId))) {
            GroupChild link = new GroupChild();
            link.setGroupId(groupId);
            link.setChildId(childId);
            groupChildRepository.save(link);
        }
    }

    @Transactional
    public void removeChildFromGroup(Long groupId, Long childId) {
        groupChildRepository.deleteByGroupIdAndChildId(groupId, childId);
    }
}