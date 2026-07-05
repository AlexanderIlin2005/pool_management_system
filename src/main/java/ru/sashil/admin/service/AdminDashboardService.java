package ru.sashil.admin.service;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.ParentWithChildren;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<ParentWithChildren> getAllParents() {
        String parentSql = "SELECT id, first_name, last_name, middle_name, email, phone FROM pool.parents ORDER BY id";
        List<Map<String, Object>> parentsRows = jdbcTemplate.queryForList(parentSql);

        List<ParentWithChildren> result = new ArrayList<>();

        for (Map<String, Object> row : parentsRows) {
            ParentWithChildren pwc = new ParentWithChildren();
            pwc.setId((Long) row.get("id"));

            // Сохраняем части ФИО отдельно для поиска
            pwc.setLastName((String) row.get("last_name"));
            pwc.setFirstName((String) row.get("first_name"));
            pwc.setMiddleName((String) row.get("middle_name"));

            // Полное ФИО для отображения
            StringBuilder fullName = new StringBuilder();
            fullName.append(row.get("last_name"));
            fullName.append(" ");
            fullName.append(row.get("first_name"));
            if (row.get("middle_name") != null && !((String)row.get("middle_name")).isEmpty()) {
                fullName.append(" ").append(row.get("middle_name"));
            }
            pwc.setFullName(fullName.toString().trim());

            pwc.setEmail((String) row.get("email"));
            pwc.setPhone((String) row.get("phone"));

            // Получаем детей
            Long parentId = (Long) row.get("id");
            String childSql = "SELECT first_name, last_name FROM pool.children WHERE parent_id = ? LIMIT 3";
            List<Map<String, Object>> childrenRows = jdbcTemplate.queryForList(childSql, parentId);

            List<String> childNames = new ArrayList<>();
            for (Map<String, Object> cRow : childrenRows) {
                String cName = cRow.get("last_name") + " " + cRow.get("first_name");
                childNames.add(cName.trim());
            }

            while (childNames.size() < 3) childNames.add("");

            pwc.setChild1(childNames.get(0));
            pwc.setChild2(childNames.get(1));
            pwc.setChild3(childNames.get(2));

            result.add(pwc);
        }
        return result;
    }

    // Метод для проверки похожести строк (Levenshtein Distance)
    public boolean isSimilar(String s1, String s2, double threshold) {
        if (s1 == null || s2 == null) return false;
        s1 = s1.toLowerCase().trim();
        s2 = s2.toLowerCase().trim();
        if (s1.equals(s2)) return true;

        LevenshteinDistance levenshtein = new LevenshteinDistance();
        int distance = levenshtein.apply(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return true;

        double similarity = 1.0 - ((double) distance / maxLen);
        return similarity >= threshold;
    }
}