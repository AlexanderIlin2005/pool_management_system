package ru.sashil.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("ru.sashil.admin.model")
@EnableJpaRepositories("ru.sashil.admin.repository")
public class AdminApplication {
    public static void run(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}