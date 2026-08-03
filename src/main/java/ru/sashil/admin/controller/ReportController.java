package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.repository.ChildRepository;
import ru.sashil.admin.service.GroupService;
import ru.sashil.admin.service.ReportService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private static final LocalDate MIN_DATE = LocalDate.of(2026, 9, 1);

    @Autowired
    private ReportService reportService;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private GroupService groupService;

    @GetMapping
    public String reportsPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "reports");
        return "reports";
    }

    // ============= ДЕТИ БЕЗ СПРАВКИ =============

    @GetMapping("/no-certificate/view")
    public String viewNoCertificateReport(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        List<Child> children = childRepository.findByCertificateReceivedFalse();

        model.addAttribute("children", children);
        model.addAttribute("total", children.size());
        model.addAttribute("reportDate", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "reports");

        return "report-no-certificate";
    }

    @GetMapping("/no-certificate/download")
    public void downloadNoCertificateReport(HttpServletResponse response, HttpSession session) throws Exception {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }

        byte[] report = reportService.generateNoCertificateReport();
        sendDocxResponse(response, report, "spisok_bez_spravki");
    }

    // ============= ОПЛАТЫ =============

    @GetMapping("/payments")
    public String paymentsReportPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        LocalDate now = LocalDate.now();
        // Начало периода - не раньше сентября 2026
        LocalDate startMonth = now.minusMonths(0).withDayOfMonth(1);
        if (startMonth.isBefore(MIN_DATE)) {
            startMonth = MIN_DATE;
        }
        LocalDate endMonth = startMonth.plusMonths(11);

        model.addAttribute("startMonth", startMonth);
        model.addAttribute("endMonth", endMonth);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "reports");

        return "report-payments";
    }

    @GetMapping("/payments/download")
    public void downloadPaymentsReport(@RequestParam String startMonth,
                                       @RequestParam String endMonth,
                                       HttpServletResponse response,
                                       HttpSession session) throws Exception {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }

        LocalDate start = LocalDate.parse(startMonth + "-01");
        LocalDate end = LocalDate.parse(endMonth + "-01");

        // Проверяем, что начало периода не раньше сентября 2026
        if (start.isBefore(MIN_DATE)) {
            start = MIN_DATE;
        }
        if (end.isBefore(start)) {
            end = start.plusMonths(11);
        }

        byte[] report = reportService.generatePaymentsReport(start, end);
        String filename = "otchet_po_oplatam_" + start.format(DateTimeFormatter.ofPattern("MM_yyyy")) +
                "_" + end.format(DateTimeFormatter.ofPattern("MM_yyyy"));
        sendDocxResponse(response, report, filename);
    }

    // ============= ПОСЕЩАЕМОСТЬ =============

    @GetMapping("/attendance")
    public String attendanceReportPage(Model model, HttpSession session,
                                       @RequestParam(required = false) Long groupId,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        List<Group> groups = groupService.getAllGroups();
        model.addAttribute("groups", groups);

        LocalDate now = LocalDate.now();
        int currentYear = year != null ? year : now.getYear();
        int currentMonth = month != null ? month : now.getMonthValue();

        String monthValue = String.format("%d-%02d", currentYear, currentMonth);
        model.addAttribute("monthValue", monthValue);

        model.addAttribute("groupId", groupId);
        model.addAttribute("year", currentYear);
        model.addAttribute("month", currentMonth);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "reports");

        return "report-attendance";
    }

    @GetMapping("/attendance/download")
    public void downloadAttendanceReport(@RequestParam Long groupId,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Integer month,
                                         @RequestParam(required = false) String monthPicker,
                                         HttpServletResponse response,
                                         HttpSession session) throws Exception {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }

        if (monthPicker != null && !monthPicker.isEmpty()) {
            String[] parts = monthPicker.split("-");
            if (parts.length == 2) {
                year = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]);
            }
        }

        if (year == null || month == null) {
            LocalDate now = LocalDate.now();
            year = now.getYear();
            month = now.getMonthValue();
        }

        byte[] report = reportService.generateAttendanceReport(groupId, year, month);

        String groupNumber = getGroupNumber(groupId);
        String filename = "otchet_poseshchaemosti_group_" + groupNumber + "_" + year + "_" + month;
        sendDocxResponse(response, report, filename);
    }

    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============

    private String getGroupNumber(Long groupId) {
        try {
            Group group = groupService.getGroupById(groupId).orElse(null);
            if (group != null && group.getNumber() != null) {
                return String.valueOf(group.getNumber());
            }
            return String.valueOf(groupId);
        } catch (Exception e) {
            return String.valueOf(groupId);
        }
    }

    private void sendDocxResponse(HttpServletResponse response, byte[] report, String filename) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                .replace("+", "%20");

        String contentDisposition = "attachment; filename*=UTF-8''" + encodedFilename +
                "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy")) + ".docx";
        response.setHeader("Content-Disposition", contentDisposition);

        response.setContentLength(report.length);
        response.getOutputStream().write(report);
        response.getOutputStream().flush();
    }
}