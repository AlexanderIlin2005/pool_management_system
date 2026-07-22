package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "join_request_notifications", schema = "pool")
@Data
public class JoinRequestNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private GroupJoinRequest request;

    @Column(name = "parent_vk_id")
    private Long parentVkId;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "is_sent")
    private Boolean isSent = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}