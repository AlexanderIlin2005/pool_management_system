package ru.sashil.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.service.DocumentService;
import ru.sashil.admin.service.AuditLogService;
import ru.sashil.admin.service.WsNotificationService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WsNotificationService wsNotificationService;

    @GetMapping
    public String documentsPage(Model model, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != AdminUser.Role.ADMIN)
            return "redirect:/login";

        model.addAttribute("fullName", currentUser.getFullName());
        model.addAttribute("role", currentUser.getRole());
        model.addAttribute("activePage", "documents");
        model.addAttribute("contracts", documentService.getHistory("CONTRACT"));
        model.addAttribute("consents", documentService.getHistory("CONSENT"));
        model.addAttribute("rules", documentService.getHistory("RULES"));
        model.addAttribute("receipts", documentService.getHistory("RECEIPT"));

        return "documents";
    }

    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file,
                                 @RequestParam("docType") String docType,
                                 HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";

        try {
            documentService.uploadDocument(file, docType, currentUser);
            auditLogService.log("DOCUMENT_UPLOADED", currentUser,
                    "Загружен документ типа " + docType + ": " + file.getOriginalFilename());
        } catch (IOException e) {
            e.printStackTrace();
            return "redirect:/documents?error";
        }

        wsNotificationService.sendUpdateNotification("DOCUMENT_UPLOADED");
        return "redirect:/documents";
    }

    @PostMapping("/activate/{id}")
    public String activateDocument(@PathVariable Long id, HttpSession session) {
        AdminUser currentUser = (AdminUser) session.getAttribute("currentUser");
        documentService.activateDocument(id);

        if (currentUser != null) {
            auditLogService.log("DOCUMENT_ACTIVATED", currentUser,
                    "Активирована версия документа ID=" + id);
        }

        wsNotificationService.sendUpdateNotification("DOCUMENT_ACTIVATED");
        return "redirect:/documents";
    }
}