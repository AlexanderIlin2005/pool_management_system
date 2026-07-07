package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.repository.AdminUserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class AdminUserService {

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<AdminUser> getAllUsers() {
        return adminUserRepository.findAll();
    }

    public Optional<AdminUser> getUserById(Long id) {
        return adminUserRepository.findById(id);
    }

    public void updateUser(AdminUser user) {
        adminUserRepository.save(user);
    }

    // Метод специально для сброса пароля
    public void updatePassword(Long id, String newPassword) {
        Optional<AdminUser> userOpt = adminUserRepository.findById(id);
        if (userOpt.isPresent()) {
            AdminUser user = userOpt.get();
            // Шифруем новый пароль перед сохранением
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            adminUserRepository.save(user);
        }
    }
}