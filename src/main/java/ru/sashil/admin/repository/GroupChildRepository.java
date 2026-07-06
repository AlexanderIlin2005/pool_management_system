package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.GroupChild;
import ru.sashil.admin.model.GroupChildId;
import java.util.List;

public interface GroupChildRepository extends JpaRepository<GroupChild, GroupChildId> {
    List<GroupChild> findByGroupId(Long groupId);
    void deleteByGroupIdAndChildId(Long groupId, Long childId);
}