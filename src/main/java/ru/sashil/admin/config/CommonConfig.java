package ru.sashil.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.sashil.common.service.DatabaseService;

@Configuration
public class CommonConfig {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Bean
    public DatabaseService databaseService() {
        return new DatabaseService(dbUrl, dbUser, dbPassword);
    }
}