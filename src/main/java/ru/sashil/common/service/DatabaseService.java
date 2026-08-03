package ru.sashil.common.service;

import ru.sashil.common.util.NameUtils;
import ru.sashil.common.util.SpringContextHolder;

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

    // === СУЩЕСТВУЮЩИЕ МЕТОДЫ (сохраняем все) ===

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

    public Long getParentIdByVkId(long vkId) {
        String sql = "SELECT id FROM pool.parents WHERE vk_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            // ОТПРАВЛЯЕМ WEB SOCKET УВЕДОМЛЕНИЕ
            sendWebSocketNotification("NEW_CERTIFICATE");
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
                "cert.date_from, cert.date_to, " +
                "p.last_name || ' ' || p.first_name as parent_name, " +
                "c.last_name || ' ' || c.first_name as child_name, " +
                "au.full_name as processed_by_name " +
                "FROM pool.certificates cert " +
                "JOIN pool.parents p ON cert.parent_id = p.id " +
                "JOIN pool.children c ON cert.child_id = c.id " +
                "LEFT JOIN pool.admin_users au ON cert.processed_by = au.id " +
                "WHERE cert.is_read = FALSE " +
                "ORDER BY cert.uploaded_at DESC";
        return executeQuery(sql);
    }

    public List<Map<String, Object>> getUnreadCertificatesForCoach(Long coachId) {
        String sql = "SELECT cert.id, cert.uploaded_at, cert.file_url, cert.status, " +
                "cert.date_from, cert.date_to, " +
                "p.last_name || ' ' || p.first_name as parent_name, " +
                "c.last_name || ' ' || c.first_name as child_name, " +
                "au.full_name as processed_by_name " +
                "FROM pool.certificates cert " +
                "JOIN pool.parents p ON cert.parent_id = p.id " +
                "JOIN pool.children c ON cert.child_id = c.id " +
                "LEFT JOIN pool.admin_users au ON cert.processed_by = au.id " +
                "JOIN pool.group_children gc ON c.id = gc.child_id " +
                "JOIN pool.groups g ON gc.group_id = g.id " +
                "WHERE cert.is_read = FALSE AND g.trainer_id = ? " +
                "GROUP BY cert.id, p.last_name, p.first_name, c.last_name, c.first_name, au.full_name " +
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
                row.put("date_from", rs.getDate("date_from"));
                row.put("date_to", rs.getDate("date_to"));
                row.put("parent_name", rs.getString("parent_name"));
                row.put("child_name", rs.getString("child_name"));
                row.put("processed_by_name", rs.getString("processed_by_name"));
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

        String processorName = "Администратор";
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
                    processorName = "Администратор";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String comment = "Справку подтвердил: " + processorName;

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

    /**
     * Отклоняет справку (без дат).
     */
    public void rejectCertificate(Long certId, Long adminId, String comment) {
        String sql;
        // Если комментарий есть - сохраняем его, если нет - оставляем NULL
        if (comment != null && !comment.trim().isEmpty()) {
            sql = "UPDATE pool.certificates SET is_read = TRUE, status = 'REJECTED', processed_by = ?, comment = ? WHERE id = ?";
        } else {
            sql = "UPDATE pool.certificates SET is_read = TRUE, status = 'REJECTED', processed_by = ? WHERE id = ?";
        }

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, adminId);
            if (comment != null && !comment.trim().isEmpty()) {
                stmt.setString(2, comment);
                stmt.setLong(3, certId);
            } else {
                stmt.setLong(2, certId);
            }
            stmt.executeUpdate();
            LOGGER.info("✅ Справка ID=" + certId + " отклонена." + (comment != null ? " Причина: " + comment : ""));

            // Отправляем WebSocket уведомление
            sendWebSocketNotification("CERTIFICATE_REJECTED");
        } catch (SQLException e) {
            LOGGER.severe("❌ Ошибка отклонения справки: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void resetCertificateReadStatus(Long certId) {
        String selectSql = "SELECT child_id, date_from, date_to FROM pool.certificates WHERE id = ?";
        Long childId = null;
        LocalDate dateFrom = null;
        LocalDate dateTo = null;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setLong(1, certId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                childId = rs.getLong("child_id");
                java.sql.Date sqlDateFrom = rs.getDate("date_from");
                java.sql.Date sqlDateTo = rs.getDate("date_to");
                if (sqlDateFrom != null) dateFrom = sqlDateFrom.toLocalDate();
                if (sqlDateTo != null) dateTo = sqlDateTo.toLocalDate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (childId != null && dateFrom != null && dateTo != null) {
            String clearCommentSql = "UPDATE pool.attendance a SET comment = NULL " +
                    "FROM pool.pool_lessons pl " +
                    "WHERE a.lesson_id = pl.id " +
                    "AND a.child_id = ? " +
                    "AND pl.lesson_date BETWEEN ? AND ? " +
                    "AND a.comment LIKE 'Справку подтвердил:%'";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(clearCommentSql)) {
                stmt.setLong(1, childId);
                stmt.setDate(2, Date.valueOf(dateFrom));
                stmt.setDate(3, Date.valueOf(dateTo));
                int updatedRows = stmt.executeUpdate();
                LOGGER.info("✅ Очищено комментариев в посещаемости: " + updatedRows);
            } catch (SQLException e) {
                LOGGER.severe("❌ Ошибка очистки комментариев: " + e.getMessage());
                e.printStackTrace();
            }
        }

        String resetSql = "UPDATE pool.certificates SET is_read = FALSE, status = 'PENDING', date_from = NULL, date_to = NULL, processed_by = NULL WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(resetSql)) {
            stmt.setLong(1, certId);
            stmt.executeUpdate();
            LOGGER.info("✅ Справка ID=" + certId + " возвращена в новые.");
        } catch (SQLException e) {
            LOGGER.severe("❌ Ошибка сброса статуса справки: " + e.getMessage());
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

    public void updateChildSkill(long childId, String newSkill) {
        String selectSql = "SELECT c.skill, p.id as parent_id FROM pool.children c " +
                "JOIN pool.parents p ON c.parent_id = p.id WHERE c.id = ?";

        String oldSkill = null;
        Long parentId = null;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setLong(1, childId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                oldSkill = rs.getString("skill");
                parentId = rs.getLong("parent_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        if (newSkill.equals(oldSkill)) {
            return;
        }

        String updateSql = "UPDATE pool.children SET skill = CAST(? AS pool.swimming_skill) WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, newSkill);
            stmt.setLong(2, childId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        String insertNotifySql = "INSERT INTO pool.skill_change_notifications (parent_id, child_id, old_skill, new_skill) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertNotifySql)) {
            stmt.setLong(1, parentId);
            stmt.setLong(2, childId);
            stmt.setString(3, oldSkill);
            stmt.setString(4, newSkill);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getPendingSkillNotifications() {
        String sql = "SELECT scn.id, scn.parent_id, scn.child_id, scn.old_skill, scn.new_skill, " +
                "p.vk_id, c.first_name as child_name " +
                "FROM pool.skill_change_notifications scn " +
                "JOIN pool.parents p ON scn.parent_id = p.id " +
                "JOIN pool.children c ON scn.child_id = c.id " +
                "WHERE scn.status = 'PENDING'";

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("parent_id", rs.getLong("parent_id"));
                row.put("vk_id", rs.getLong("vk_id"));
                row.put("child_name", rs.getString("child_name"));
                row.put("old_skill", rs.getString("old_skill"));
                row.put("new_skill", rs.getString("new_skill"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void markSkillNotificationSent(long notificationId) {
        String sql = "UPDATE pool.skill_change_notifications SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notificationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // === НОВЫЕ МЕТОДЫ ДЛЯ ЗАПИСИ В ГРУППУ ===

    /**
     * Находит подходящие группы для ребенка по возрасту и навыку.
     * Возвращает список групп, отсортированный по приоритету совпадения.
     */

    public List<Map<String, Object>> findSuitableGroupsForChild(long childId) {
        String sql = "SELECT c.age, c.skill::text as skill FROM pool.children c WHERE c.id = ?";
        int age = 0;
        String skill = null;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, childId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                age = rs.getInt("age");
                skill = rs.getString("skill");
            } else {
                return java.util.Collections.emptyList();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return java.util.Collections.emptyList();
        }

        String groupsSql = "SELECT g.id, g.name, g.number, g.min_age, g.max_age, g.skill_1, g.skill_2, " +
                "g.day_1_start, g.day_1_end, g.day_2_start, g.day_2_end, " +
                "g.day_3_start, g.day_3_end, g.day_4_start, g.day_4_end, " +
                "g.day_5_start, g.day_5_end " +
                "FROM pool.groups g ORDER BY g.number";

        List<Map<String, Object>> allGroups = executeQuery(groupsSql);

        List<Map<String, Object>> fullMatch = new ArrayList<>();
        List<Map<String, Object>> skillMatch = new ArrayList<>();
        List<Map<String, Object>> ageMatch = new ArrayList<>();

        for (Map<String, Object> g : allGroups) {
            Integer minAge = (Integer) g.get("min_age");
            Integer maxAge = (Integer) g.get("max_age");
            String s1 = (String) g.get("skill_1");
            String s2 = (String) g.get("skill_2");

            boolean ageOk = (minAge == null || age >= minAge) && (maxAge == null || age <= maxAge);
            boolean skillOk = (s1 == null && s2 == null) ||
                    (skill != null && (skill.equals(s1) || skill.equals(s2)));

            if (ageOk && skillOk) fullMatch.add(g);
            else if (skillOk) skillMatch.add(g);
            else if (ageOk) ageMatch.add(g);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(fullMatch);
        result.addAll(skillMatch);
        result.addAll(ageMatch);
        return result;
    }



    /**
     * Создает заявку на вступление в группу и уведомление для администратора.
     */
    public void createJoinRequest(long parentVkId, long childId, long groupId) throws SQLException {
        // Сначала получаем parent_id по vk_id
        Long parentId = getParentIdByVkId(parentVkId);
        if (parentId == null) {
            throw new SQLException("Родитель с VK ID " + parentVkId + " не найден");
        }

        // Создаем заявку с parent_id
        String sql = "INSERT INTO pool.group_join_requests (parent_id, child_id, group_id, status, created_at) " +
                "VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setLong(2, childId);
            stmt.setLong(3, groupId);
            stmt.executeUpdate();
        }

        // Создаем уведомление для администраторов
        String insertNotifSql = "INSERT INTO pool.join_request_notifications (parent_vk_id, message_text) " +
                "VALUES (?, ?)";

        String groupName = "";
        String childName = "";

        // Получаем данные для уведомления
        String getDataSql = "SELECT g.name as group_name, c.first_name, c.last_name " +
                "FROM pool.groups g, pool.children c " +
                "WHERE g.id = ? AND c.id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(getDataSql)) {
            stmt.setLong(1, groupId);
            stmt.setLong(2, childId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                groupName = rs.getString("group_name");
                childName = rs.getString("first_name") + " " + rs.getString("last_name");
            }
        }

        String message = "Новая заявка на запись!\n\n" +
                "Ребенок: " + childName + "\n" +
                "Группа: " + groupName;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertNotifSql)) {
            stmt.setLong(1, parentVkId);
            stmt.setString(2, message);
            stmt.executeUpdate();
        }

        // ОТПРАВЛЯЕМ WEB SOCKET УВЕДОМЛЕНИЕ
        sendWebSocketNotification("NEW_JOIN_REQUEST");
    }

    /**
     * Получает нерассыланные уведомления о заявках.
     */
    public List<Map<String, Object>> getPendingJoinRequestNotifications() {
        String sql = "SELECT jrn.id, jrn.parent_vk_id, jrn.message_text " +
                "FROM pool.join_request_notifications jrn " +
                "WHERE jrn.is_sent = FALSE ORDER BY jrn.created_at ASC";
        return executeQuery(sql);
    }

    /**
     * Помечает уведомление о заявке как отправленное.
     */
    public void markJoinRequestNotificationSent(long notifId) {
        String sql = "UPDATE pool.join_request_notifications SET is_sent = TRUE, sent_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notifId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // В DatabaseService.java добавляем:

    /**
     * Сохраняет квитанцию об оплате от родителя.
     */
    /**
     * Сохраняет квитанцию об оплате от родителя.
     */
    public void savePaymentReceipt(long parentVkId, long childId, LocalDate monthYear, String fileUrl, String originalName) {
        Long parentId = getParentIdByVkId(parentVkId);
        if (parentId == null) {
            LOGGER.severe("Ошибка: Родитель с VK ID " + parentVkId + " не найден в базе.");
            return;
        }

        // Сначала проверяем, есть ли уже запись об оплате за этот месяц
        String checkSql = "SELECT id FROM pool.payments WHERE child_id = ? AND month_year = ?";
        Long paymentId = null;
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setLong(1, childId);
            stmt.setDate(2, Date.valueOf(monthYear));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                paymentId = rs.getLong("id");
                LOGGER.info("Найдена существующая оплата ID=" + paymentId);
            } else {
                LOGGER.info("Оплата не найдена, создаем новую");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String sql;
        if (paymentId != null) {
            sql = "UPDATE pool.payments SET receipt_file_url = ?, receipt_original_name = ?, status = 'PENDING', updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        } else {
            sql = "INSERT INTO pool.payments (child_id, month_year, receipt_file_url, receipt_original_name, status, amount) VALUES (?, ?, ?, ?, 'PENDING', 4000.00)";
        }

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (paymentId != null) {
                stmt.setString(1, fileUrl);
                stmt.setString(2, originalName);
                stmt.setLong(3, paymentId);
                LOGGER.info("Обновляем оплату ID=" + paymentId);
            } else {
                stmt.setLong(1, childId);
                stmt.setDate(2, Date.valueOf(monthYear));
                stmt.setString(3, fileUrl);
                stmt.setString(4, originalName);
                LOGGER.info("Создаем новую оплату для childId=" + childId);
            }
            int rows = stmt.executeUpdate();
            LOGGER.info("✅ Квитанция сохранена для child_id=" + childId + ", month=" + monthYear + ", rows=" + rows);

            // ОТПРАВЛЯЕМ WEB SOCKET УВЕДОМЛЕНИЕ
            sendWebSocketNotification("NEW_PAYMENT_RECEIPT");
        } catch (SQLException e) {
            LOGGER.severe("❌ Ошибка сохранения квитанции: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Получает необработанные квитанции об оплате (для бухгалтера).
     */
    public List<Map<String, Object>> getPendingPayments() {
        String sql = "SELECT p.id, p.child_id, p.month_year, p.receipt_file_url, p.receipt_original_name, " +
                "p.status, p.created_at, " +
                "c.first_name, c.last_name, " +
                "par.vk_id as parent_vk_id " +
                "FROM pool.payments p " +
                "JOIN pool.children c ON p.child_id = c.id " +
                "JOIN pool.parents par ON c.parent_id = par.id " +
                "WHERE p.status = 'PENDING' " +
                "ORDER BY p.created_at DESC";
        return executeQuery(sql);
    }

    /**
     * Получает неотправленные уведомления об оплате.
     */
    public List<Map<String, Object>> getPendingPaymentNotifications() {
        String sql = "SELECT pn.id, pn.parent_vk_id, pn.message_text " +
                "FROM pool.payment_notifications pn " +
                "WHERE pn.is_sent = FALSE " +
                "ORDER BY pn.created_at ASC";
        return executeQuery(sql);
    }

    /**
     * Помечает уведомление об оплате как отправленное.
     */
    public void markPaymentNotificationSent(long notifId) {
        String sql = "UPDATE pool.payment_notifications SET is_sent = TRUE, sent_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notifId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Отправляет WebSocket уведомление об обновлении данных.
     */
    private void sendWebSocketNotification(String eventType) {
        try {
            // Пытаемся получить сервис из Spring контекста
            Object wsService = SpringContextHolder.getBean("wsNotificationService");
            if (wsService != null) {
                // Используем рефлексию для вызова метода
                java.lang.reflect.Method method = wsService.getClass().getMethod("sendUpdateNotification", String.class);
                method.invoke(wsService, eventType);
                LOGGER.info("✅ WebSocket уведомление отправлено: " + eventType);
            }
        } catch (Exception e) {
            // Если Spring еще не инициализирован (при запуске бота), просто логируем
            LOGGER.fine("WebSocket уведомление не отправлено (Spring не инициализирован): " + e.getMessage());
        }
    }



    /**
     * Получает неотправленные уведомления об изменении данных ребенка.
     */
    public List<Map<String, Object>> getPendingChildUpdateNotifications() {
        String sql = "SELECT cun.id, cun.parent_vk_id, cun.message_text " +
                "FROM pool.child_update_notifications cun " +
                "WHERE cun.is_sent = FALSE " +
                "ORDER BY cun.created_at ASC";
        return executeQuery(sql);
    }

    /**
     * Помечает уведомление об изменении данных ребенка как отправленное.
     */
    public void markChildUpdateNotificationSent(long notifId) {
        String sql = "UPDATE pool.child_update_notifications SET is_sent = TRUE, sent_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notifId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    // Добавьте эти методы в DatabaseService.java

    /**
     * Сохраняет уведомление о пропуске занятия
     */
    public void saveAbsenceNotification(long parentVkId, long childId, String type, String message) {
        Long parentId = getParentIdByVkId(parentVkId);
        if (parentId == null) return;

        String sql = "INSERT INTO pool.absence_notifications (parent_id, child_id, absence_type, message, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setLong(2, childId);
            stmt.setString(3, type);
            stmt.setString(4, message);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Ошибка сохранения уведомления о пропуске: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Получает все уведомления о пропусках для администратора
     */
    public List<Map<String, Object>> getAllAbsenceNotifications() {
        String sql = "SELECT an.id, an.absence_type, an.message, an.status, an.created_at, " +
                "p.last_name as parent_last_name, p.first_name as parent_first_name, " +
                "c.last_name as child_last_name, c.first_name as child_first_name " +
                "FROM pool.absence_notifications an " +
                "JOIN pool.parents p ON an.parent_id = p.id " +
                "JOIN pool.children c ON an.child_id = c.id " +
                "ORDER BY an.created_at DESC";
        return executeQuery(sql);
    }

    /**
     * Получает уведомления о пропусках для конкретного тренера
     */
    public List<Map<String, Object>> getAbsenceNotificationsForCoach(Long coachId) {
        String sql = "SELECT an.id, an.absence_type, an.message, an.status, an.created_at, " +
                "p.last_name as parent_last_name, p.first_name as parent_first_name, " +
                "c.last_name as child_last_name, c.first_name as child_first_name " +
                "FROM pool.absence_notifications an " +
                "JOIN pool.parents p ON an.parent_id = p.id " +
                "JOIN pool.children c ON an.child_id = c.id " +
                "JOIN pool.group_children gc ON c.id = gc.child_id " +
                "JOIN pool.groups g ON gc.group_id = g.id " +
                "WHERE g.trainer_id = ? " +
                "ORDER BY an.created_at DESC";

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, coachId);
            ResultSet rs = stmt.executeQuery();
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

    /**
     * Обновляет статус уведомления о пропуске
     */
    public void updateAbsenceNotificationStatus(long notificationId, String status, Long adminId) {
        String sql = "UPDATE pool.absence_notifications SET status = ?, updated_at = CURRENT_TIMESTAMP, processed_by = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            if (adminId != null) {
                stmt.setLong(2, adminId);
            } else {
                stmt.setNull(2, java.sql.Types.BIGINT);
            }
            stmt.setLong(3, notificationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    // === ВСПОМОГАТЕЛЬНЫЙ МЕТОД ===

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