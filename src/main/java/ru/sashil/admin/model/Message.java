package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages", schema = "pool")
@Data
public class Message {

    public enum UserType {
        PARENT, ADMIN, COACH
    }

    public enum Status {
        PENDING("Ожидает"),
        READ("Прочитано"),
        REPLIED("Отвечено");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "from_user_type", nullable = false)
    private String fromUserType;

    @Column(name = "to_user_id")
    private Long toUserId;

    @Column(name = "to_user_type", nullable = false)
    private String toUserType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "message_text", columnDefinition = "TEXT", nullable = false)
    private String messageText;

    @Column(name = "status")
    private String status = "PENDING";

    @Column(name = "parent_message_id")
    private Long parentMessageId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}