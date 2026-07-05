package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.Pool;

public interface PoolRepository extends JpaRepository<Pool, Long> {
}