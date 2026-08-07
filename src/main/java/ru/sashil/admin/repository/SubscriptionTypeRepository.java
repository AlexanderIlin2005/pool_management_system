package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.SubscriptionType;

import java.util.List;
import java.util.Optional;

public interface SubscriptionTypeRepository extends JpaRepository<SubscriptionType, Long> {
    Optional<SubscriptionType> findByDisplayName(String displayName);
    boolean existsByDisplayName(String displayName);

    List<SubscriptionType> findAllByOrderByCreatedAtAsc();
}