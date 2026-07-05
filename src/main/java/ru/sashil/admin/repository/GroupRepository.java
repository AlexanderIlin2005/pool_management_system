package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {
    boolean existsByNumber(Integer number);
}