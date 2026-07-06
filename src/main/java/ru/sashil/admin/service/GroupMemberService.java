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
        String sql = "SELECT c.id, c.first_name, c.last_name, c.age, c.grade_number FROM pool.children c " +
                "JOIN pool.group_children gc ON c.id = gc.child_id WHERE gc.group_id = ? ORDER BY c.last_name";
        return jdbcTemplate.queryForList(sql, groupId);
    }

    // Возвращаем пустой список, если search пустой или null
    public List<Map<String, Object>> getAvailableChildren(Long groupId, String search) {
        if (search == null || search.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String sql = "SELECT id, first_name, last_name, age, grade_number FROM pool.children " +
                "WHERE id NOT IN (SELECT child_id FROM pool.group_children WHERE group_id = ?) " +
                "AND (first_name ILIKE ? OR last_name ILIKE ? OR CAST(age AS TEXT) = ? OR CAST(grade_number AS TEXT) = ?) " +
                "ORDER BY last_name";

        String likeSearch = "%" + search + "%";
        return jdbcTemplate.queryForList(sql, groupId, likeSearch, likeSearch, search, search);
    }

    public void addChildToGroup(Long groupId, Long childId) {
        if (!groupChildRepository.existsById(new GroupChildId(groupId, childId))) {
            GroupChild link = new GroupChild();
            link.setGroupId(groupId);
            link.setChildId(childId);
            groupChildRepository.save(link);
        }
    }

    @Transactional // Важно для операции удаления
    public void removeChildFromGroup(Long groupId, Long childId) {
        groupChildRepository.deleteByGroupIdAndChildId(groupId, childId);
    }
}