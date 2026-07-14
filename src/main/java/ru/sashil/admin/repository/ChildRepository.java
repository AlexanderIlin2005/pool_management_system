package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.sashil.admin.model.Child;
import java.util.List;

@Repository
public interface ChildRepository extends JpaRepository<Child, Long> {

    @Query(value = "SELECT c.* FROM pool.children c " +
            "JOIN pool.group_children gc ON c.id = gc.child_id " +
            "WHERE gc.group_id = :groupId",
            nativeQuery = true)
    List<Child> findByGroupIdNative(Long groupId);
}