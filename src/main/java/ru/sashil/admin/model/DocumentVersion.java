package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_versions", schema = "pool")
@Data
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "is_active")
    private Boolean isActive;

    @ManyToOne
    @JoinColumn(name = "admin_id")
    private AdminUser admin;
}