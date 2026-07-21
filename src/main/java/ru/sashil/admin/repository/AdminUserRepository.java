package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.AdminUser;
import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByLogin(String login);

    
    List<AdminUser> findByRole(AdminUser.Role role);
}