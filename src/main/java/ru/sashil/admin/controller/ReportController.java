package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.repository.ChildRepository;
import ru.sashil.admin.service.ReportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ChildRepository childRepository;

    @GetMapping
    public String reportsPage(Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "reports");
        return "reports";
    }

    // Новая страница предпросмотра отчета "Дети без справки"
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

    // Скачивание отчета
    @GetMapping("/no-certificate/download")
    public void downloadNoCertificateReport(HttpServletResponse response, HttpSession session) throws Exception {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }

        try {
            byte[] report = reportService.generateNoCertificateReport();

            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition",
                    "attachment; filename=spisok_bez_spravki_" +
                            LocalDate.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy")) +
                            ".docx");
            response.setContentLength(report.length);
            response.getOutputStream().write(report);
            response.getOutputStream().flush();

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Ошибка генерации отчета");
        }
    }
}