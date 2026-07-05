package ru.sashil.admin.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "pools", schema = "pool")
@Data
public class Pool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;
}