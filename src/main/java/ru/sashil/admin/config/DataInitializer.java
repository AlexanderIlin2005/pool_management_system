package ru.sashil.admin.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.repository.AdminUserRepository;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(AdminUserRepository repository, BCryptPasswordEncoder encoder) {
        return args -> {
            if (repository.count() == 0) {
                createAdmin(repository, encoder, "admin", "admin123", "Главный Администратор");
                createAdmin(repository, encoder, "buh", "buh123", "Иванова Мария Ивановна");
                createAdmin(repository, encoder, "coach", "coach123", "Петров Сергей Сергеевич");
                System.out.println("Тестовые администраторы созданы.");
            }
        };
    }

    private void createAdmin(AdminUserRepository repo, BCryptPasswordEncoder encoder, String login, String pass, String name) {
        AdminUser user = new AdminUser();
        user.setLogin(login);
        user.setPasswordHash(encoder.encode(pass)); // Шифруем пароль
        user.setFullName(name);

        if (login.equals("admin")) user.setRole(AdminUser.Role.ADMIN);
        else if (login.equals("buh")) user.setRole(AdminUser.Role.ACCOUNTANT);
        else user.setRole(AdminUser.Role.COACH);

        repo.save(user);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}