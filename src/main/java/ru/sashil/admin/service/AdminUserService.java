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

    public void saveUser(AdminUser user) {
        // Если пароль не пустой, шифруем его перед сохранением
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            // Проверяем, не зашифрован ли он уже (простая проверка на длину bcrypt хеша)
            if (!user.getPasswordHash().startsWith("$2a$") && !user.getPasswordHash().startsWith("$2b$")) {
                user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
            }
        }
        adminUserRepository.save(user);
    }

    public void deleteUser(Long id) {
        adminUserRepository.deleteById(id);
    }

    public boolean checkPassword(Long userId, String rawPassword) {
        Optional<AdminUser> userOpt = adminUserRepository.findById(userId);
        if (userOpt.isPresent()) {
            return passwordEncoder.matches(rawPassword, userOpt.get().getPasswordHash());
        }
        return false;
    }
}