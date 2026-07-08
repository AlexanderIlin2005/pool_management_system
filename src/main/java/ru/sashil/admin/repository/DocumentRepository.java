package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.DocumentVersion;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentVersion, Long> {
    Optional<DocumentVersion> findByDocTypeAndIsActiveTrue(String docType);
    List<DocumentVersion> findByDocTypeOrderByUploadedAtDesc(String docType);

    // Метод для принудительного сброса всех активных документов определенного типа
    @Modifying
    @Transactional
    @Query("UPDATE DocumentVersion d SET d.isActive = false WHERE d.docType = :docType")
    void deactivateAllByDocType(String docType);
}