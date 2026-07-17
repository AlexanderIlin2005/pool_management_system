package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.sashil.admin.model.Child;
import ru.sashil.admin.model.ChildSimple;

import java.util.List;

@Repository
public interface ChildRepository extends JpaRepository<Child, Long> {

    /**
     * ИСПРАВЛЕНИЕ: Возвращаем только ID, чтобы избежать ошибки маппинга Enum SwimmingSkill
     * в нативном запросе.
     */
    @Query(value = "SELECT c.id FROM pool.children c " +
            "JOIN pool.group_children gc ON c.id = gc.child_id " +
            "WHERE gc.group_id = :groupId",
            nativeQuery = true)
    List<Long> findIdsByGroupIdNative(Long groupId);

    // ИСПРАВЛЕНИЕ: Добавили c.middleName в выборку и в конструктор
    @Query("SELECT new ru.sashil.admin.model.ChildSimple(c.id, c.firstName, c.lastName, c.middleName) " +
            "FROM Child c JOIN GroupChild gc ON c.id = gc.childId WHERE gc.groupId = :groupId")
    List<ChildSimple> findSimpleByGroupId(Long groupId);
}