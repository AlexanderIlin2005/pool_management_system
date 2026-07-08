package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.DocumentVersion;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentVersion, Long> {
    Optional<DocumentVersion> findByDocTypeAndIsActiveTrue(String docType);
    List<DocumentVersion> findByDocTypeOrderByUploadedAtDesc(String docType);
}