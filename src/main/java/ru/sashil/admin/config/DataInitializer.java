package ru.sashil.admin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.repository.AdminUserRepository;
import ru.sashil.admin.repository.PoolRepository;

import java.util.List;

@Configuration
public class DataInitializer {

    @Autowired
    private PoolRepository poolRepository;

    @Bean
    public CommandLineRunner initData(AdminUserRepository repository, BCryptPasswordEncoder encoder) {
        return args -> {
            // Инициализация администраторов
            if (repository.count() == 0) {
                createAdmin(repository, encoder, "admin", "admin123", "Главный Администратор");
                createAdmin(repository, encoder, "buh", "buh123", "Иванова Мария Ивановна");
                createAdmin(repository, encoder, "coach", "coach123", "Петров Сергей Сергеевич");
                System.out.println("✅ Тестовые администраторы созданы.");
            }

            // Инициализация бассейнов
            if (poolRepository.count() == 0) {
                Pool p1 = new Pool();
                p1.setName("Бассейн 1");
                p1.setAddress("ул. Спортивная, 1");

                Pool p2 = new Pool();
                p2.setName("Бассейн 2");
                p2.setAddress("пр. Мира, 15");

                Pool p3 = new Pool();
                p3.setName("Бассейн 3");
                p3.setAddress("ул. Ленина, 42");

                poolRepository.saveAll(List.of(p1, p2, p3));
                System.out.println("✅ Тестовые бассейны созданы.");
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