package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "group_children", schema = "pool")
@Data
@IdClass(GroupChildId.class) // Используем составной ключ
public class GroupChild {
    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "child_id")
    private Long childId;
}