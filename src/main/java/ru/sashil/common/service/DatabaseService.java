package ru.sashil.common.service;

import ru.sashil.common.util.NameUtils;
import java.sql.*;
import java.time.LocalDate;
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

    public void addChild(long parentVkId, String fName, String lName, String mName, String birthDate, int gradeNum, String gradeName, String skill) throws SQLException {
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

        String sql = "INSERT INTO pool.children (parent_id, first_name, last_name, middle_name, birth_date, grade_number, grade_name, skill) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::pool.swimming_skill)";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setString(2, fName);
            stmt.setString(3, lName);
            stmt.setString(4, mName);
            stmt.setDate(5, Date.valueOf(birthDate));
            stmt.setInt(6, gradeNum);
            stmt.setString(7, gradeName);
            stmt.setString(8, skill);
            stmt.executeUpdate();
            LOGGER.info("Ребенок добавлен для родителя VK:" + parentVkId);
        }
    }

    public List<Map<String, Object>> getChildrenByParentVkId(long parentVkId) throws SQLException {
        List<Map<String, Object>> children = new ArrayList<>();
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

    public Map<String, Object> getActiveDocument(String docType) throws SQLException {
        String sql = "SELECT file_name FROM pool.document_versions WHERE doc_type = ? AND is_active = TRUE LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, docType);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("fileName", rs.getString("file_name"));
                return doc;
            }
        }
        return null;
    }

    public boolean hasNotificationBeenSent(long parentId, long childId, String type, LocalDate date) {
        String sql = "SELECT COUNT(*) FROM pool.notification_log WHERE parent_id = ? AND child_id = ? AND notification_type = ? AND lesson_date = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setLong(2, childId);
            stmt.setString(3, type);
            stmt.setDate(4, Date.valueOf(date));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void logNotificationSent(long parentId, long childId, String type, LocalDate date) {
        String sql = "INSERT INTO pool.notification_log (parent_id, child_id, notification_type, lesson_date) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setLong(2, childId);
            stmt.setString(3, type);
            stmt.setDate(4, Date.valueOf(date));
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getChildrenScheduleForDate(long parentId, LocalDate date) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT c.id as child_id, g.name as group_name, g.number as group_number, pl.start_time, pl.end_time, p.name as pool_name, au.full_name as trainer_name " +
                "FROM pool.children c " +
                "JOIN pool.group_children gc ON c.id = gc.child_id " +
                "JOIN pool.groups g ON gc.group_id = g.id " +
                "LEFT JOIN pool.pool_lessons pl ON g.id = pl.group_id AND pl.lesson_date = ? " +
                "LEFT JOIN pool.pools p ON g.pool_id = p.id " +
                "LEFT JOIN pool.admin_users au ON g.trainer_id = au.id " +
                "WHERE c.parent_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(date));
            stmt.setLong(2, parentId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("childId", rs.getLong("child_id"));
                row.put("groupName", rs.getString("group_name"));
                row.put("groupNumber", rs.getInt("group_number"));
                row.put("startTime", rs.getTime("start_time"));
                row.put("endTime", rs.getTime("end_time"));
                row.put("poolName", rs.getString("pool_name"));
                row.put("trainerName", rs.getString("trainer_name"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public boolean isRegularNotificationsEnabled(long parentId) {
        String sql = "SELECT notify_regular FROM pool.parents WHERE vk_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Boolean val = rs.getBoolean("notify_regular");
                return !rs.wasNull() && val;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void toggleRegularNotifications(long parentId, boolean enabled) {
        String sql = "UPDATE pool.parents SET notify_regular = ? WHERE vk_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, enabled);
            stmt.setLong(2, parentId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Long getParentIdByVkId(long vkId) {
        String sql = "SELECT id FROM pool.parents WHERE vk_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, vkId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveCertificate(Long parentVkId, Long childId, String fileUrl) {
        Long parentId = getParentIdByVkId(parentVkId);
        if (parentId == null) {
            LOGGER.severe("Ошибка: Родитель с VK ID " + parentVkId + " не найден в базе.");
            return;
        }

        String sql = "INSERT INTO pool.certificates (parent_id, child_id, file_url, uploaded_at, is_read, status) VALUES (?, ?, ?, CURRENT_TIMESTAMP, FALSE, 'PENDING')";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setLong(2, childId);
            stmt.setString(3, fileUrl);
            stmt.executeUpdate();
            LOGGER.info("✅ Справка сохранена для parent_id=" + parentId + ", child_id=" + childId);
        } catch (SQLException e) {
            LOGGER.severe("❌ Ошибка сохранения справки: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getChildrenForParent(Long vkId) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT c.id, c.first_name, c.last_name FROM pool.children c JOIN pool.parents p ON c.parent_id = p.id WHERE p.vk_id = ? ORDER BY c.birth_date DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, vkId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("name", rs.getString("last_name") + " " + rs.getString("first_name"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<Map<String, Object>> getUnreadCertificates() {
        String sql = "SELECT cert.id, cert.uploaded_at, cert.file_url, cert.status, " +
                "p.last_name || ' ' || p.first_name as parent_name, " +
                "c.last_name || ' ' || c.first_name as child_name " +
                "FROM pool.certificates cert " +
                "JOIN pool.parents p ON cert.parent_id = p.id " +
                "JOIN pool.children c ON cert.child_id = c.id " +
                "WHERE cert.is_read = FALSE " +
                "ORDER BY cert.uploaded_at DESC";
        return executeQuery(sql);
    }

    public List<Map<String, Object>> getUnreadCertificatesForCoach(Long coachId) {
        String sql = "SELECT cert.id, cert.uploaded_at, cert.file_url, cert.status, " +
                "p.last_name || ' ' || p.first_name as parent_name, " +
                "c.last_name || ' ' || c.first_name as child_name " +
                "FROM pool.certificates cert " +
                "JOIN pool.parents p ON cert.parent_id = p.id " +
                "JOIN pool.children c ON cert.child_id = c.id " +
                "JOIN pool.group_children gc ON c.id = gc.child_id " +
                "JOIN pool.groups g ON gc.group_id = g.id " +
                "WHERE cert.is_read = FALSE AND g.trainer_id = ? " +
                "GROUP BY cert.id, p.last_name, p.first_name, c.last_name, c.first_name " +
                "ORDER BY cert.uploaded_at DESC";

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, coachId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("uploaded_at", rs.getTimestamp("uploaded_at"));
                row.put("file_url", rs.getString("file_url"));
                row.put("status", rs.getString("status"));
                row.put("parent_name", rs.getString("parent_name"));
                row.put("child_name", rs.getString("child_name"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void processCertificate(Long certId, Long adminId, String status, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) return;

        String dbStatus = "APPROVED_SICK".equals(status) ? "SICK" : "EXCUSED";

        String processorName = "Администратором";
        String sqlGetName = "SELECT full_name, role FROM pool.admin_users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlGetName)) {
            stmt.setLong(1, adminId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String fullName = rs.getString("full_name");
                String role = rs.getString("role");

                if ("COACH".equals(role)) {
                    processorName = "Тренер " + NameUtils.toInitials(fullName);
                } else {
                    processorName = "Администратором";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String comment = "Подтверждено справкой ID=" + certId + ". " + processorName;

        String updateCertSql = "UPDATE pool.certificates SET is_read = TRUE, status = ?, date_from = ?, date_to = ?, processed_by = ? WHERE id = ?";

        String updateAttendanceSql = "INSERT INTO pool.attendance (lesson_id, child_id, status, marked_by, marked_at, comment) " +
                "SELECT pl.id, gc.child_id, ?, ?, CURRENT_TIMESTAMP, ? " +
                "FROM pool.group_children gc " +
                "JOIN pool.pool_lessons pl ON pl.group_id = gc.group_id " +
                "WHERE gc.child_id = (SELECT child_id FROM pool.certificates WHERE id = ?) " +
                "AND pl.lesson_date BETWEEN ? AND ? " +
                "ON CONFLICT (lesson_id, child_id) DO UPDATE SET status = EXCLUDED.status, comment = EXCLUDED.comment";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt1 = conn.prepareStatement(updateCertSql)) {
                    stmt1.setString(1, status);
                    stmt1.setDate(2, Date.valueOf(dateFrom));
                    stmt1.setDate(3, Date.valueOf(dateTo));
                    stmt1.setLong(4, adminId);
                    stmt1.setLong(5, certId);
                    stmt1.executeUpdate();
                }

                try (PreparedStatement stmt2 = conn.prepareStatement(updateAttendanceSql)) {
                    stmt2.setString(1, dbStatus);
                    stmt2.setLong(2, adminId);
                    stmt2.setString(3, comment);
                    stmt2.setLong(4, certId);
                    stmt2.setDate(5, Date.valueOf(dateFrom));
                    stmt2.setDate(6, Date.valueOf(dateTo));
                    stmt2.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void resetCertificateReadStatus(Long certId) {
        String sql = "UPDATE pool.certificates SET is_read = FALSE, status = 'PENDING', date_from = NULL, date_to = NULL, processed_by = NULL WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, certId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getReadCertificates() {
        String sql = "SELECT cert.id, cert.uploaded_at, cert.file_url, cert.status, cert.date_from, cert.date_to, " +
                "p.last_name || ' ' || p.first_name as parent_name, " +
                "c.last_name || ' ' || c.first_name as child_name, " +
                "au.full_name as processed_by_name " +
                "FROM pool.certificates cert " +
                "JOIN pool.parents p ON cert.parent_id = p.id " +
                "JOIN pool.children c ON cert.child_id = c.id " +
                "LEFT JOIN pool.admin_users au ON cert.processed_by = au.id " +
                "WHERE cert.is_read = TRUE " +
                "ORDER BY cert.uploaded_at DESC";
        return executeQuery(sql);
    }

    private List<Map<String, Object>> executeQuery(String sql) {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int columns = md.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
}