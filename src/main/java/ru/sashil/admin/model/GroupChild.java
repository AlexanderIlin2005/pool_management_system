package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_children", schema = "pool")
@Data
@IdClass(GroupChildId.class)
public class GroupChild {
    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "child_id")
    private Long childId;

    // ДОБАВЛЕНО: Поле для даты добавления ребенка в группу (соответствует БД)
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}