package ru.sashil.admin.service;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.repository.ChildRepository;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    @Autowired
    private ChildRepository childRepository;

    /**
     * Генерирует отчет в формате DOCX со списком детей, не предоставивших справку.
     */
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
        titleRun.setText("Дата: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
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

            XWPFParagraph p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            if (p.getRuns().isEmpty()) {
                p.createRun();
            }
            XWPFRun run = p.getRuns().get(0);
            run.setBold(true);
            run.setFontSize(12);
        }

        // === ДАННЫЕ ===
        int counter = 1;
        for (Child child : children) {
            XWPFTableRow row = table.getRow(counter);

            // №
            XWPFTableCell cell0 = row.getCell(0);
            cell0.setText(String.valueOf(counter));
            cell0.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);

            // ФИО ребенка
            XWPFTableCell cell1 = row.getCell(1);
            String fullName = child.getLastName() + " " + child.getFirstName();
            if (child.getMiddleName() != null && !child.getMiddleName().isEmpty()) {
                fullName += " " + child.getMiddleName();
            }
            cell1.setText(fullName);

            // Возраст
            XWPFTableCell cell2 = row.getCell(2);
            cell2.setText(String.valueOf(child.getAge() != null ? child.getAge() : "-"));
            cell2.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);

            // Класс
            XWPFTableCell cell3 = row.getCell(3);
            cell3.setText(String.valueOf(child.getGradeNumber() != null ? child.getGradeNumber() : "-"));
            cell3.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);

            // Телефон родителя
            XWPFTableCell cell4 = row.getCell(4);
            String phone = "-";
            if (child.getParent() != null && child.getParent().getPhone() != null) {
                phone = child.getParent().getPhone();
            }
            cell4.setText(phone);
            cell4.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);

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
        footerRun.setText("(Администратор)");
        footerRun.addBreak();
        footerRun.addBreak();

        // === ПОДВАЛ (убрали) ===

        // === СОХРАНЯЕМ ===
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        document.write(baos);
        document.close();

        return baos.toByteArray();
    }
}