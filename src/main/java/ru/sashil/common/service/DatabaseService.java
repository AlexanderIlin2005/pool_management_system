package ru.sashil.bot.handlers;

import java.sql.*;
import java.util.HashMap;
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


}