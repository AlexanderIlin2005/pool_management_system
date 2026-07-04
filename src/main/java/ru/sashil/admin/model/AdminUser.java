package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "admin_users", schema = "pool")
@Data
public class AdminUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String login;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role {
        ADMIN, ACCOUNTANT, COACH
    }
}