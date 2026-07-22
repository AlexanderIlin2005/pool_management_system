package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_join_requests", schema = "pool")
@Data
public class GroupJoinRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(length = 20)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String adminComment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}