package ru.sashil.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Сканируем весь пакет ru.sashil, чтобы найти и Admin, и Common компоненты
@SpringBootApplication(scanBasePackages = "ru.sashil")
@EntityScan(basePackages = {"ru.sashil.admin.model", "ru.sashil.common.model"}) // Если есть модели в common
@EnableJpaRepositories(basePackages = {"ru.sashil.admin.repository", "ru.sashil.common.repository"}) // Если есть репозитории в common
public class AdminApplication {
    public static void run(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}