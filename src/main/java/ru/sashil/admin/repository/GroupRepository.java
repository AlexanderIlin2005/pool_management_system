package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.Group;
import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    boolean existsByNumber(Integer number);

    // ИСПРАВЛЕНИЕ: явное указание пути к полю id связанной сущности
    List<Group> findByTrainer_Id(Long trainerId);
    List<Group> findByPool_Id(Long poolId);
    List<Group> findByTrainer_IdAndPool_Id(Long trainerId, Long poolId);
}