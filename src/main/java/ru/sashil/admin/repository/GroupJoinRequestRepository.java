package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.sashil.admin.model.GroupJoinRequest;
import java.util.List;

@Repository
public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    List<GroupJoinRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<GroupJoinRequest> findByParentIdAndStatus(Long parentId, String status);

    // Новый метод для получения обработанных заявок
    @Query("SELECT r FROM GroupJoinRequest r WHERE r.status IN ('APPROVED', 'REJECTED') ORDER BY r.processedAt DESC")
    List<GroupJoinRequest> findProcessedOrderByProcessedAtDesc();

    int countByStatus(String status);
}