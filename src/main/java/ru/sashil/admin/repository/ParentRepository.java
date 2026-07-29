package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.Parent;

public interface ParentRepository extends JpaRepository<Parent, Long> {
}