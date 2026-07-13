package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sashil.admin.model.Child;
import java.util.List;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findByParentId(Long parentId);

    @Query("SELECT c FROM Child c JOIN GroupChild gc ON c.id = gc.childId WHERE gc.groupId = :groupId")
    List<Child> findByGroupId(@Param("groupId") Long groupId);
}