package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.DocumentVersion;
import ru.sashil.admin.repository.DocumentRepository;
import ru.sashil.common.config.MinIOConfig;
import ru.sashil.common.service.MinIOService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private MinIOService minIOService;

    public void uploadDocument(MultipartFile file, String docType, AdminUser admin) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Файл пуст");

        String extension = ".pdf";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        String uniqueFileName = UUID.randomUUID().toString() + extension;

        try {
            minIOService.uploadFileToDocsBucket(file.getInputStream(), uniqueFileName, file.getSize());
        } catch (Exception e) {
            throw new IOException("Ошибка загрузки в MinIO", e);
        }

        DocumentVersion doc = new DocumentVersion();
        doc.setDocType(docType);
        doc.setFileName(uniqueFileName);
        doc.setOriginalName(originalName);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setIsActive(false);
        doc.setAdmin(admin);

        documentRepository.save(doc);
    }

    @Transactional
    public void activateDocument(Long versionId) {
        DocumentVersion doc = documentRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Версия не найдена"));

        
        if (Boolean.TRUE.equals(doc.getIsActive())) {
            return;
        }

        
        
        documentRepository.deactivateAllByDocType(doc.getDocType());

        
        doc.setIsActive(true);
        documentRepository.save(doc);
    }

    public List<DocumentVersion> getHistory(String docType) {
        return documentRepository.findByDocTypeOrderByUploadedAtDesc(docType);
    }

    public String getDownloadUrl(String fileName) {
        return MinIOConfig.getEndpoint() + "/" + MinIOConfig.getDocsBucket() + "/" + fileName;
    }
}