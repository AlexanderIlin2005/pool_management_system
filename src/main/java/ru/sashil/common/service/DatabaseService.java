package ru.sashil.common.service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DatabaseService {
    private static final Logger LOGGER = Logger.getLogger(DatabaseService.class.getName());
    private final String url;
    private final String user;
    private final String password;

    public DatabaseService(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void saveParent(long vkId, String firstName, String lastName, String middleName, String email) throws SQLException {
        String sql = "INSERT INTO pool.parents (vk_id, first_name, last_name, middle_name, email) VALUES (?, ?, ?, ?, ?) ON CONFLICT (vk_id) DO UPDATE SET first_name=EXCLUDED.first_name, last_name=EXCLUDED.last_name, email=EXCLUDED.email";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, vkId);
            stmt.setString(2, firstName);
            stmt.setString(3, lastName);
            stmt.setString(4, middleName);
            stmt.setString(5, email);
            stmt.executeUpdate();
            LOGGER.info("✅ Родитель VK:" + vkId + " сохранен/обновлен.");
        }
    }

    public boolean isParentRegistered(long vkId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM pool.parents WHERE vk_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, vkId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        }
        return false;
    }

    public void updateParent(long vkId, String firstName, String lastName, String middleName, String email, String phone) throws SQLException {
        String sql = "UPDATE pool.parents SET first_name = ?, last_name = ?, middle_name = ?, email = ?, phone = ? WHERE vk_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setString(3, middleName);
            stmt.setString(4, email);
            stmt.setString(5, phone);
            stmt.setLong(6, vkId);
            stmt.executeUpdate();
            LOGGER.info("✅ Данные родителя VK:" + vkId + " обновлены.");
        }
    }

    public Map<String, String> getParentData(long vkId) throws SQLException {
        String sql = "SELECT first_name, last_name, middle_name, email, phone FROM pool.parents WHERE vk_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, vkId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, String> data = new HashMap<>();
                // getString вернет null, если в базе NULL. Это нормально.
                data.put("firstName", rs.getString("first_name"));
                data.put("lastName", rs.getString("last_name"));
                data.put("middleName", rs.getString("middle_name"));
                data.put("email", rs.getString("email"));
                data.put("phone", rs.getString("phone"));
                return data;
            }
        }
        return null;
    }

    // Вставь этот метод в класс DatabaseService

    public void addChild(long parentVkId, String fName, String lName, String mName, String birthDate, int gradeNum, String gradeName, String skill) throws SQLException {
        // Сначала находим ID родителя по его VK ID
        String findParentSql = "SELECT id FROM pool.parents WHERE vk_id = ?";
        Long parentId = null;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(findParentSql)) {
            stmt.setLong(1, parentVkId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                parentId = rs.getLong("id");
            } else {
                throw new SQLException("Родитель с VK ID " + parentVkId + " не найден в базе.");
            }
        }

        // ВНИМАНИЕ: age здесь НЕТ, его посчитает триггер
        String sql = "INSERT INTO pool.children (parent_id, first_name, last_name, middle_name, birth_date, grade_number, grade_name, skill) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::pool.swimming_skill)";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setString(2, fName);
            stmt.setString(3, lName);
            stmt.setString(4, mName);
            stmt.setDate(5, Date.valueOf(birthDate)); // Формат YYYY-MM-DD от DateUtils
            stmt.setInt(6, gradeNum);
            stmt.setString(7, gradeName);
            stmt.setString(8, skill);
            stmt.executeUpdate();
            LOGGER.info("Ребенок добавлен для родителя VK:" + parentVkId);
        }
    }

    // Получить список всех детей родителя по его VK ID
    // Получить список всех детей родителя по его VK ID
    public List<Map<String, Object>> getChildrenByParentVkId(long parentVkId) throws SQLException {
        List<Map<String, Object>> children = new ArrayList<>();
        // Исправлено: добавлены псевдонимы c (children) и p (parents)
        // и явное указание c.id для выбора ID ребенка
        String sql = "SELECT c.id, c.first_name, c.last_name, c.middle_name, c.birth_date, c.age, c.grade_number, c.grade_name, c.skill::text FROM pool.children c " +
                "JOIN pool.parents p ON c.parent_id = p.id WHERE p.vk_id = ?";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentVkId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> child = new HashMap<>();
                child.put("id", rs.getLong("id"));
                child.put("firstName", rs.getString("first_name"));
                child.put("lastName", rs.getString("last_name"));
                child.put("middleName", rs.getString("middle_name"));

                // Обработка даты может вернуть null, если поле пустое, но у нас NOT NULL
                java.sql.Date sqlDate = rs.getDate("birth_date");
                child.put("birthDate", sqlDate != null ? sqlDate.toString() : "");

                child.put("age", rs.getInt("age"));
                child.put("gradeNumber", rs.getInt("grade_number"));
                child.put("gradeName", rs.getString("grade_name"));
                child.put("skill", rs.getString("skill"));
                children.add(child);
            }
        }
        return children;
    }

    // Обновить данные конкретного ребенка
    public void updateChild(long childId, String fName, String lName, String mName, String birthDate, int gradeNum, String gradeName, String skill) throws SQLException {
        String sql = "UPDATE pool.children SET first_name = ?, last_name = ?, middle_name = ?, birth_date = ?, grade_number = ?, grade_name = ?, skill = ?::pool.swimming_skill WHERE id = ?";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fName);
            stmt.setString(2, lName);
            stmt.setString(3, mName);
            stmt.setDate(4, Date.valueOf(birthDate));
            stmt.setInt(5, gradeNum);
            stmt.setString(6, gradeName);
            stmt.setString(7, skill);
            stmt.setLong(8, childId);
            stmt.executeUpdate();
        }
    }





}