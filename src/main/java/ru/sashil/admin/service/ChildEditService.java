package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.dto.ChildEditDto;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.model.ChildUpdateNotification;
import ru.sashil.admin.model.Parent;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.repository.ChildRepository;
import ru.sashil.admin.repository.ChildUpdateNotificationRepository;
import ru.sashil.admin.repository.ParentRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class ChildEditService {

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ChildUpdateNotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WsNotificationService wsNotificationService;

    /**
     * Получает данные ребенка для редактирования.
     */
    public ChildEditDto getChildForEdit(Long childId) {
        String sql = "SELECT c.id, c.first_name, c.last_name, c.middle_name, c.birth_date, c.age, " +
                "c.grade_number, c.grade_name, c.skill::text, " +
                "p.id as parent_id, p.first_name as parent_first_name, p.last_name as parent_last_name, " +
                "p.email as parent_email, p.phone as parent_phone " +
                "FROM pool.children c " +
                "JOIN pool.parents p ON c.parent_id = p.id " +
                "WHERE c.id = ?";

        Map<String, Object> row = jdbcTemplate.queryForMap(sql, childId);

        ChildEditDto dto = new ChildEditDto();
        dto.setChildId((Long) row.get("id"));
        dto.setFirstName((String) row.get("first_name"));
        dto.setLastName((String) row.get("last_name"));
        dto.setMiddleName((String) row.get("middle_name"));

        java.sql.Date sqlDate = (java.sql.Date) row.get("birth_date");
        if (sqlDate != null) {
            dto.setBirthDate(sqlDate.toLocalDate());
        }

        dto.setAge((Integer) row.get("age"));
        dto.setGradeNumber((Integer) row.get("grade_number"));
        dto.setGradeName((String) row.get("grade_name"));
        dto.setSkill((String) row.get("skill"));
        dto.setParentId((Long) row.get("parent_id"));
        dto.setParentFirstName((String) row.get("parent_first_name"));
        dto.setParentLastName((String) row.get("parent_last_name"));
        dto.setParentEmail((String) row.get("parent_email"));
        dto.setParentPhone((String) row.get("parent_phone"));

        return dto;
    }

    /**
     * Сохраняет изменения ребенка и отправляет уведомление родителю.
     */
    @Transactional
    public void updateChild(ChildEditDto dto, AdminUser actor) {
        // Получаем текущие данные
        ChildEditDto oldData = getChildForEdit(dto.getChildId());

        // 1. Обновляем ребенка через прямой SQL (как в отдельной форме навыка)
        String updateChildSql = "UPDATE pool.children SET " +
                "first_name = ?, " +
                "last_name = ?, " +
                "middle_name = ?, " +
                "birth_date = ?, " +
                "grade_number = ?, " +
                "grade_name = ? " +
                "WHERE id = ?";

        jdbcTemplate.update(updateChildSql,
                dto.getFirstName(),
                dto.getLastName(),
                dto.getMiddleName(),
                dto.getBirthDate(),
                dto.getGradeNumber(),
                dto.getGradeName(),
                dto.getChildId()
        );

        // 2. Обновляем навык отдельно через CAST (как в отдельной форме навыка)
        if (dto.getSkill() != null && !dto.getSkill().equals(oldData.getSkill())) {
            String updateSkillSql = "UPDATE pool.children SET skill = CAST(? AS pool.swimming_skill) WHERE id = ?";
            jdbcTemplate.update(updateSkillSql, dto.getSkill(), dto.getChildId());
        }

        // 3. Обновляем родителя
        String updateParentSql = "UPDATE pool.parents SET " +
                "first_name = ?, " +
                "last_name = ?, " +
                "email = ?, " +
                "phone = ? " +
                "WHERE id = ?";

        jdbcTemplate.update(updateParentSql,
                dto.getParentFirstName(),
                dto.getParentLastName(),
                dto.getParentEmail(),
                dto.getParentPhone(),
                dto.getParentId()
        );

        // 4. Получаем обновленные данные для уведомления
        Child child = childRepository.findById(dto.getChildId())
                .orElseThrow(() -> new RuntimeException("Ребенок не найден"));

        Parent parent = parentRepository.findById(dto.getParentId())
                .orElseThrow(() -> new RuntimeException("Родитель не найден"));

        // 5. Создаем уведомление для родителя
        String message = buildNotificationMessage(oldData, dto);
        if (message != null && !message.isEmpty()) {
            ChildUpdateNotification notification = new ChildUpdateNotification();
            notification.setParentVkId(parent.getVkId());
            notification.setChild(child);
            notification.setMessageText(message);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setIsSent(false);
            notificationRepository.save(notification);
        }

        // 6. Логируем изменение
        auditLogService.log("CHILD_DATA_UPDATED", actor,
                "Обновлены данные ребенка \"" + child.getLastName() + " " + child.getFirstName() +
                        "\" (ID=" + child.getId() + ")");

        wsNotificationService.sendUpdateNotification("CHILD_DATA_UPDATED");
    }

    /**
     * Формирует сообщение об изменениях для родителя.
     */
    private String buildNotificationMessage(ChildEditDto oldData, ChildEditDto newData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Данные вашего ребенка были обновлены администратором:\n\n");

        boolean hasChanges = false;

        // Проверяем изменения ребенка
        if (!oldData.getLastName().equals(newData.getLastName())) {
            sb.append("• Фамилия: ").append(oldData.getLastName()).append(" → ").append(newData.getLastName()).append("\n");
            hasChanges = true;
        }
        if (!oldData.getFirstName().equals(newData.getFirstName())) {
            sb.append("• Имя: ").append(oldData.getFirstName()).append(" → ").append(newData.getFirstName()).append("\n");
            hasChanges = true;
        }
        if (!safeEquals(oldData.getMiddleName(), newData.getMiddleName())) {
            sb.append("• Отчество: ").append(nullToDash(oldData.getMiddleName())).append(" → ").append(nullToDash(newData.getMiddleName())).append("\n");
            hasChanges = true;
        }
        if (!oldData.getBirthDate().equals(newData.getBirthDate())) {
            sb.append("• Дата рождения: ").append(formatDate(oldData.getBirthDate())).append(" → ").append(formatDate(newData.getBirthDate())).append("\n");
            hasChanges = true;
        }
        if (!safeEquals(String.valueOf(oldData.getGradeNumber()), String.valueOf(newData.getGradeNumber()))) {
            sb.append("• Класс: ").append(oldData.getGradeNumber()).append(" → ").append(newData.getGradeNumber()).append("\n");
            hasChanges = true;
        }
        if (!safeEquals(oldData.getGradeName(), newData.getGradeName())) {
            sb.append("• Название класса: ").append(nullToDash(oldData.getGradeName())).append(" → ").append(nullToDash(newData.getGradeName())).append("\n");
            hasChanges = true;
        }
        if (!safeEquals(oldData.getSkill(), newData.getSkill())) {
            sb.append("• Навык плавания: ").append(oldData.getSkill()).append(" → ").append(newData.getSkill()).append("\n");
            hasChanges = true;
        }

        // Проверяем изменения родителя
        if (!oldData.getParentLastName().equals(newData.getParentLastName())) {
            sb.append("• Ваша фамилия: ").append(oldData.getParentLastName()).append(" → ").append(newData.getParentLastName()).append("\n");
            hasChanges = true;
        }
        if (!oldData.getParentFirstName().equals(newData.getParentFirstName())) {
            sb.append("• Ваше имя: ").append(oldData.getParentFirstName()).append(" → ").append(newData.getParentFirstName()).append("\n");
            hasChanges = true;
        }
        if (!safeEquals(oldData.getParentEmail(), newData.getParentEmail())) {
            sb.append("• Ваш email: ").append(nullToDash(oldData.getParentEmail())).append(" → ").append(nullToDash(newData.getParentEmail())).append("\n");
            hasChanges = true;
        }
        if (!safeEquals(oldData.getParentPhone(), newData.getParentPhone())) {
            sb.append("• Ваш телефон: ").append(nullToDash(oldData.getParentPhone())).append(" → ").append(nullToDash(newData.getParentPhone())).append("\n");
            hasChanges = true;
        }

        if (!hasChanges) {
            return null;
        }

        sb.append("\nЕсли вы не запрашивали изменения, пожалуйста, свяжитесь с администрацией.");
        return sb.toString();
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private String nullToDash(String value) {
        return value != null && !value.isEmpty() ? value : "-";
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }
}