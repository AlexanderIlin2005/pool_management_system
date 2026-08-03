package ru.sashil.admin.service;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.model.Payment;
import ru.sashil.admin.repository.ChildRepository;
import ru.sashil.admin.repository.PaymentRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("MM.yyyy");

    // Русская локаль для форматирования месяцев (родительный падеж)
    private static final Locale RUSSIAN_LOCALE = new Locale("ru", "RU");
    private static final DateTimeFormatter MONTH_GENITIVE_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", RUSSIAN_LOCALE);

    // Маппинг месяцев для именительного падежа
    private static final Map<Integer, String> MONTH_NOMINATIVE = new HashMap<>();
    static {
        MONTH_NOMINATIVE.put(1, "январь");
        MONTH_NOMINATIVE.put(2, "февраль");
        MONTH_NOMINATIVE.put(3, "март");
        MONTH_NOMINATIVE.put(4, "апрель");
        MONTH_NOMINATIVE.put(5, "май");
        MONTH_NOMINATIVE.put(6, "июнь");
        MONTH_NOMINATIVE.put(7, "июль");
        MONTH_NOMINATIVE.put(8, "август");
        MONTH_NOMINATIVE.put(9, "сентябрь");
        MONTH_NOMINATIVE.put(10, "октябрь");
        MONTH_NOMINATIVE.put(11, "ноябрь");
        MONTH_NOMINATIVE.put(12, "декабрь");
    }

    /**
     * Возвращает название месяца в именительном падеже с годом
     */
    private String getMonthNominative(LocalDate date) {
        return MONTH_NOMINATIVE.get(date.getMonthValue()) + " " + date.getYear();
    }

    // ============= ОТЧЕТ ПО ОПЛАТАМ =============

    public byte[] generatePaymentsReport(LocalDate startMonth, LocalDate endMonth) throws Exception {
        List<Child> children = childRepository.findAll();
        List<Payment> payments = paymentRepository.findPaymentsInPeriod(startMonth, endMonth);

        // Группируем оплаты по детям
        Map<Long, Map<LocalDate, Payment>> paymentMap = new HashMap<>();
        for (Payment p : payments) {
            paymentMap.computeIfAbsent(p.getChild().getId(), k -> new HashMap<>())
                    .put(p.getMonthYear(), p);
        }

        // Список месяцев в периоде
        List<LocalDate> months = new ArrayList<>();
        LocalDate current = startMonth;
        while (!current.isAfter(endMonth)) {
            months.add(current);
            current = current.plusMonths(1);
        }

        // Фильтруем детей, у которых есть группы
        List<Child> childrenWithGroups = new ArrayList<>();
        for (Child child : children) {
            if (checkChildHasGroup(child.getId())) {
                childrenWithGroups.add(child);
            }
        }

        XWPFDocument document = new XWPFDocument();

        // ===== ЗАГОЛОВОК =====
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(18);
        titleRun.setText("ОТЧЕТ ПО ОПЛАТАМ АБОНЕМЕНТОВ");
        titleRun.addBreak();

        titleRun.setFontSize(14);
        titleRun.setBold(false);
        titleRun.setText("Бассейн Гимназии №642 \"Земля и Вселенная\"");
        titleRun.addBreak();

        // Для первого месяца используем родительный падеж (дефолтный от DateTimeFormatter)
        // "с Сентября 2026 по Май 2027"
        String startMonthStr = startMonth.format(MONTH_GENITIVE_FORMATTER);  // родительный падеж (дефолтный)
        String endMonthStr = getMonthNominative(endMonth);                   // именительный падеж
        titleRun.setText("Период: с " + startMonthStr + " по " + endMonthStr);
        titleRun.addBreak();
        titleRun.addBreak();

        // ===== ТАБЛИЦА =====
        int baseCols = 5; // №, ФИО, Возраст, Класс, Группа
        int colCount = baseCols + months.size() + 2; // +2 для Итого и Долга

        XWPFTable table = document.createTable(childrenWithGroups.size() + 1, colCount);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);

        // ===== ЗАГОЛОВКИ ТАБЛИЦЫ =====
        XWPFTableRow headerRow = table.getRow(0);
        int col = 0;

        String[] fixedHeaders = {"№", "ФИО ребенка", "Возраст", "Класс", "Группа"};
        for (String header : fixedHeaders) {
            XWPFTableCell cell = headerRow.getCell(col);
            if (cell == null) {
                cell = headerRow.addNewTableCell();
            }
            cell.setText(header);
            formatHeaderCell(cell);
            col++;
        }

        // Месяцы - формат MM.yyyy
        for (LocalDate month : months) {
            XWPFTableCell cell = headerRow.getCell(col);
            if (cell == null) {
                cell = headerRow.addNewTableCell();
            }
            cell.setText(month.format(MONTH_SHORT));
            formatHeaderCell(cell);
            col++;
        }

        // Итого
        XWPFTableCell totalHeader = headerRow.getCell(col);
        if (totalHeader == null) {
            totalHeader = headerRow.addNewTableCell();
        }
        totalHeader.setText("Итого (₽)");
        formatHeaderCell(totalHeader);
        col++;

        // Долг
        XWPFTableCell debtHeader = headerRow.getCell(col);
        if (debtHeader == null) {
            debtHeader = headerRow.addNewTableCell();
        }
        debtHeader.setText("Долг (₽)");
        formatHeaderCell(debtHeader);

        // ===== ДАННЫЕ =====
        int rowNum = 1;
        int childCounter = 1;
        BigDecimal grandTotalPaid = BigDecimal.ZERO;
        BigDecimal grandTotalDebt = BigDecimal.ZERO;

        for (Child child : childrenWithGroups) {
            XWPFTableRow row = table.getRow(rowNum++);
            col = 0;

            // №
            XWPFTableCell cellNum = row.getCell(col);
            if (cellNum == null) cellNum = row.addNewTableCell();
            cellNum.setText(String.valueOf(childCounter++));
            formatCellCenter(cellNum);
            col++;

            // ФИО
            String fullName = child.getLastName() + " " + child.getFirstName();
            if (child.getMiddleName() != null && !child.getMiddleName().isEmpty()) {
                fullName += " " + child.getMiddleName();
            }
            XWPFTableCell cellName = row.getCell(col);
            if (cellName == null) cellName = row.addNewTableCell();
            cellName.setText(fullName);
            col++;

            // Возраст
            XWPFTableCell cellAge = row.getCell(col);
            if (cellAge == null) cellAge = row.addNewTableCell();
            cellAge.setText(String.valueOf(child.getAge() != null ? child.getAge() : "-"));
            formatCellCenter(cellAge);
            col++;

            // Класс
            XWPFTableCell cellGrade = row.getCell(col);
            if (cellGrade == null) cellGrade = row.addNewTableCell();
            cellGrade.setText(String.valueOf(child.getGradeNumber() != null ? child.getGradeNumber() : "-"));
            formatCellCenter(cellGrade);
            col++;

            // Группа
            String groupName = getChildGroupName(child.getId());
            XWPFTableCell cellGroup = row.getCell(col);
            if (cellGroup == null) cellGroup = row.addNewTableCell();
            cellGroup.setText(groupName);
            col++;

            // Месяцы - показываем суммы оплат
            BigDecimal totalPaid = BigDecimal.ZERO;

            Map<LocalDate, Payment> childPayments = paymentMap.getOrDefault(child.getId(), Collections.emptyMap());

            for (LocalDate month : months) {
                Payment payment = childPayments.get(month);
                XWPFTableCell cell = row.getCell(col);
                if (cell == null) cell = row.addNewTableCell();

                if (payment != null && payment.getTotalPaid() != null && payment.getTotalPaid().compareTo(BigDecimal.ZERO) > 0) {
                    String amountStr = payment.getTotalPaid().toString();
                    cell.setText(amountStr);
                    totalPaid = totalPaid.add(payment.getTotalPaid());
                } else {
                    cell.setText("");
                }
                formatCellCenter(cell);
                col++;
            }

            // Итого оплачено
            XWPFTableCell cellTotal = row.getCell(col);
            if (cellTotal == null) cellTotal = row.addNewTableCell();
            cellTotal.setText(totalPaid.toString());
            formatCellCenter(cellTotal);
            col++;

            // Долг
            BigDecimal totalDebtAmount = BigDecimal.ZERO;
            for (LocalDate month : months) {
                Payment payment = childPayments.get(month);
                if (payment != null) {
                    BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
                    BigDecimal totalPaidMonth = payment.getTotalPaid() != null ? payment.getTotalPaid() : BigDecimal.ZERO;
                    if (amount.compareTo(BigDecimal.ZERO) > 0 && totalPaidMonth.compareTo(amount) < 0) {
                        totalDebtAmount = totalDebtAmount.add(amount.subtract(totalPaidMonth));
                    }
                }
            }

            XWPFTableCell cellDebt = row.getCell(col);
            if (cellDebt == null) cellDebt = row.addNewTableCell();
            cellDebt.setText(totalDebtAmount.toString());
            formatCellCenter(cellDebt);

            grandTotalPaid = grandTotalPaid.add(totalPaid);
            grandTotalDebt = grandTotalDebt.add(totalDebtAmount);
        }

        // ===== ИТОГОВАЯ СТРОКА =====
        XWPFTableRow totalRow = table.createRow();
        for (int i = 0; i < colCount; i++) {
            XWPFTableCell cell = totalRow.getCell(i);
            if (cell == null) {
                cell = totalRow.addNewTableCell();
            }

            if (i == 0) {
                cell.setText("ИТОГО:");
                XWPFParagraph p = cell.getParagraphs().get(0);
                p.setAlignment(ParagraphAlignment.LEFT);
                if (p.getRuns().isEmpty()) p.createRun();
                p.getRuns().get(0).setBold(true);
            } else if (i == colCount - 2) {
                cell.setText(grandTotalPaid.toString());
                formatCellCenter(cell);
                XWPFParagraph p = cell.getParagraphs().get(0);
                if (p.getRuns().isEmpty()) p.createRun();
                p.getRuns().get(0).setBold(true);
            } else if (i == colCount - 1) {
                cell.setText(grandTotalDebt.toString());
                formatCellCenter(cell);
                XWPFParagraph p = cell.getParagraphs().get(0);
                if (p.getRuns().isEmpty()) p.createRun();
                p.getRuns().get(0).setBold(true);
            } else {
                cell.setText("");
                formatCellCenter(cell);
            }
        }

        // ===== ПОДПИСЬ =====
        XWPFParagraph footer = document.createParagraph();
        footer.setSpacingBefore(200);
        XWPFRun footerRun = footer.createRun();
        footerRun.setBold(false);
        footerRun.setFontSize(12);
        footerRun.setText("Подпись: ___________________");
        footerRun.addBreak();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        document.write(baos);
        document.close();
        return baos.toByteArray();
    }

    // ============= ОТЧЕТ ПО ПОСЕЩАЕМОСТИ =============

    public byte[] generateAttendanceReport(Long groupId, int year, int month) throws Exception {
        String groupName = getGroupName(groupId);

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // Получаем детей в группе
        String childrenSql = "SELECT c.id, c.first_name, c.last_name, c.middle_name, c.age, c.grade_number " +
                "FROM pool.children c " +
                "JOIN pool.group_children gc ON c.id = gc.child_id " +
                "WHERE gc.group_id = ? " +
                "ORDER BY c.last_name, c.first_name";
        List<Map<String, Object>> children = jdbcTemplate.queryForList(childrenSql, groupId);

        // Получаем занятия за месяц
        String lessonsSql = "SELECT id, lesson_date FROM pool.pool_lessons " +
                "WHERE group_id = ? AND lesson_date BETWEEN ? AND ? " +
                "ORDER BY lesson_date";
        List<Map<String, Object>> lessons = jdbcTemplate.queryForList(lessonsSql, groupId, startDate, endDate);

        // Получаем ID занятий
        List<Long> lessonIds = lessons.stream()
                .map(l -> ((Number) l.get("id")).longValue())
                .collect(Collectors.toList());

        // Получаем посещаемость
        Map<Long, Map<Long, String>> attendanceMap = new HashMap<>();
        if (!lessonIds.isEmpty()) {
            String attSql = "SELECT lesson_id, child_id, status FROM pool.attendance WHERE lesson_id IN (" +
                    lessonIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
            List<Map<String, Object>> attRows = jdbcTemplate.queryForList(attSql);
            for (Map<String, Object> row : attRows) {
                Long lessonId = ((Number) row.get("lesson_id")).longValue();
                Long childId = ((Number) row.get("child_id")).longValue();
                String status = (String) row.get("status");
                attendanceMap.computeIfAbsent(lessonId, k -> new HashMap<>()).put(childId, status);
            }
        }

        XWPFDocument document = new XWPFDocument();

        // ===== ЗАГОЛОВОК =====
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(18);
        titleRun.setText("ОТЧЕТ ПО ПОСЕЩАЕМОСТИ");
        titleRun.addBreak();

        titleRun.setFontSize(14);
        titleRun.setBold(false);
        titleRun.setText("Бассейн Гимназии №642 \"Земля и Вселенная\"");
        titleRun.addBreak();
        titleRun.setText("Группа: " + groupName);
        titleRun.addBreak();

        // Для одиночного месяца используем именительный падеж
        String monthStr = getMonthNominative(startDate);
        titleRun.setText("Месяц: " + monthStr);
        titleRun.addBreak();
        titleRun.addBreak();

        // ===== ТАБЛИЦА =====
        int colCount = 3 + lessons.size(); // ФИО, Возраст, Класс + даты занятий
        XWPFTable table = document.createTable(children.size() + 1, colCount);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);

        // ===== ЗАГОЛОВКИ =====
        XWPFTableRow headerRow = table.getRow(0);
        int col = 0;

        String[] fixedHeaders = {"ФИО ребенка", "Возраст", "Класс"};
        for (String h : fixedHeaders) {
            XWPFTableCell cell = headerRow.getCell(col++);
            cell.setText(h);
            formatHeaderCell(cell);
        }

        // Даты занятий
        List<LocalDate> lessonDates = new ArrayList<>();
        for (Map<String, Object> lesson : lessons) {
            LocalDate date = ((java.sql.Date) lesson.get("lesson_date")).toLocalDate();
            lessonDates.add(date);
            XWPFTableCell cell = headerRow.getCell(col++);
            cell.setText(date.format(DateTimeFormatter.ofPattern("dd.MM")));
            formatHeaderCell(cell);
        }

        // ===== ДАННЫЕ =====
        int rowNum = 1;
        Map<Long, Map<Long, String>> finalAttendanceMap = attendanceMap;

        for (Map<String, Object> childRow : children) {
            Long childId = ((Number) childRow.get("id")).longValue();
            XWPFTableRow row = table.getRow(rowNum++);
            col = 0;

            // ФИО
            String fullName = childRow.get("last_name") + " " + childRow.get("first_name");
            String middleName = (String) childRow.get("middle_name");
            if (middleName != null && !middleName.isEmpty()) {
                fullName += " " + middleName;
            }
            setCellValue(row.getCell(col++), fullName, false);

            // Возраст
            setCellValue(row.getCell(col++), String.valueOf(childRow.get("age") != null ? childRow.get("age") : "-"), true);

            // Класс
            setCellValue(row.getCell(col++), String.valueOf(childRow.get("grade_number") != null ? childRow.get("grade_number") : "-"), true);

            // Посещаемость по датам
            for (Long lessonId : lessonIds) {
                String status = finalAttendanceMap.getOrDefault(lessonId, Collections.emptyMap()).get(childId);
                XWPFTableCell cell = row.getCell(col++);

                if ("PRESENT".equals(status)) {
                    cell.setText("П");
                } else if ("ABSENT".equals(status)) {
                    cell.setText("О");
                } else if ("SICK".equals(status)) {
                    cell.setText("Б");
                } else if ("EXCUSED".equals(status)) {
                    cell.setText("У");
                } else {
                    cell.setText("•");
                }
                formatCellCenter(cell);
            }
        }

        // ===== ИТОГО ПОСЕЩАЕМОСТЬ =====
        XWPFParagraph info = document.createParagraph();
        info.setSpacingBefore(200);
        XWPFRun infoRun = info.createRun();
        infoRun.setFontSize(12);
        infoRun.setText("Всего занятий: " + lessons.size());
        infoRun.addBreak();
        infoRun.setText("Всего детей: " + children.size());
        infoRun.addBreak();
        infoRun.addBreak();

        // Легенда
        infoRun.setText("Легенда: П — Присутствовал, О — Отсутствовал, Б — Болел, У — Уважительная причина, • — Нет данных");
        infoRun.addBreak();
        infoRun.addBreak();

        // ===== ПОДПИСЬ =====
        infoRun.setText("Подпись: ___________________");
        infoRun.addBreak();
        infoRun.setText("(Администратор)");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        document.write(baos);
        document.close();
        return baos.toByteArray();
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============

    private void formatHeaderCell(XWPFTableCell cell) {
        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        if (p.getRuns().isEmpty()) p.createRun();
        XWPFRun run = p.getRuns().get(0);
        run.setBold(true);
        run.setFontSize(11);
        cell.setColor("E8E8E8");
    }

    private void formatCellCenter(XWPFTableCell cell) {
        cell.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);
    }

    private void setCellValue(XWPFTableCell cell, String value, boolean center) {
        cell.setText(value != null ? value : "-");
        if (center) {
            cell.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);
        }
    }

    private boolean checkChildHasGroup(Long childId) {
        String sql = "SELECT COUNT(*) FROM pool.group_children WHERE child_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, childId);
        return count != null && count > 0;
    }

    private String getChildGroupName(Long childId) {
        String sql = "SELECT g.name FROM pool.groups g " +
                "JOIN pool.group_children gc ON g.id = gc.group_id " +
                "WHERE gc.child_id = ? LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, childId);
        } catch (Exception e) {
            return "-";
        }
    }

    private String getGroupName(Long groupId) {
        String sql = "SELECT name FROM pool.groups WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, groupId);
        } catch (Exception e) {
            return "Группа #" + groupId;
        }
    }

    // ============= ОТЧЕТ "Дети без справки" =============

    public byte[] generateNoCertificateReport() throws Exception {
        List<Child> children = childRepository.findByCertificateReceivedFalse();

        XWPFDocument document = new XWPFDocument();

        // === ЗАГОЛОВОК ===
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(18);
        titleRun.setText("СПИСОК ДЕТЕЙ, НЕ ПРЕДОСТАВИВШИХ МЕДИЦИНСКУЮ СПРАВКУ");
        titleRun.addBreak();

        titleRun.setFontSize(14);
        titleRun.setBold(false);
        titleRun.setText("Бассейн Гимназии №642 \"Земля и Вселенная\"");
        titleRun.addBreak();
        titleRun.setText("Дата: " + LocalDate.now().format(DATE_FORMAT));
        titleRun.addBreak();
        titleRun.addBreak();

        // === ТАБЛИЦА ===
        XWPFTable table = document.createTable(children.size() + 1, 5);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);

        // === ЗАГОЛОВКИ ТАБЛИЦЫ ===
        XWPFTableRow headerRow = table.getRow(0);
        String[] headers = {"№", "ФИО ребенка", "Возраст", "Класс", "Телефон родителя"};
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell cell = headerRow.getCell(i);
            cell.setText(headers[i]);
            formatHeaderCell(cell);
        }

        // === ДАННЫЕ ===
        int counter = 1;
        for (Child child : children) {
            XWPFTableRow row = table.getRow(counter);

            setCellValue(row.getCell(0), String.valueOf(counter), true);

            String fullName = child.getLastName() + " " + child.getFirstName();
            if (child.getMiddleName() != null && !child.getMiddleName().isEmpty()) {
                fullName += " " + child.getMiddleName();
            }
            setCellValue(row.getCell(1), fullName, false);
            setCellValue(row.getCell(2), String.valueOf(child.getAge() != null ? child.getAge() : "-"), true);
            setCellValue(row.getCell(3), String.valueOf(child.getGradeNumber() != null ? child.getGradeNumber() : "-"), true);

            String phone = "-";
            if (child.getParent() != null && child.getParent().getPhone() != null) {
                phone = child.getParent().getPhone();
            }
            setCellValue(row.getCell(4), phone, true);

            counter++;
        }

        // === ИТОГ ===
        XWPFParagraph footer = document.createParagraph();
        footer.setSpacingBefore(200);
        XWPFRun footerRun = footer.createRun();
        footerRun.setBold(true);
        footerRun.setFontSize(14);
        footerRun.setText("ИТОГО: " + children.size() + " детей");
        footerRun.addBreak();
        footerRun.addBreak();

        footerRun.setBold(false);
        footerRun.setFontSize(12);
        footerRun.setText("Подпись: ___________________");
        footerRun.addBreak();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        document.write(baos);
        document.close();
        return baos.toByteArray();
    }
}