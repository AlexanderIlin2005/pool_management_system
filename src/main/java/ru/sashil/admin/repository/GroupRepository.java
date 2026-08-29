package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.Group;
import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    boolean existsByNumber(Integer number);


    List<Group> findByTrainerId(Long trainerId);
    List<Group> findByPool_Id(Long poolId);
    List<Group> findByTrainerIdAndPoolId(Long trainerId, Long poolId);
}