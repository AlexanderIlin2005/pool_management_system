package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.logging.Logger;

@Service
public class AgeUpdateScheduler {

    private static final Logger LOGGER = Logger.getLogger(AgeUpdateScheduler.class.getName());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Обновляет возраст всех детей каждый день в 12:00.
     * Cron: 0 0 12 * * ?  - каждый день в 12:00
     */
    @Scheduled(cron = "0 0 12 * * ?")
    @Transactional
    public void updateAllAges() {
        LOGGER.info("🔄 Запуск обновления возраста детей...");
        long startTime = System.currentTimeMillis();

        try {
            int updated = jdbcTemplate.update(
                    "UPDATE pool.children SET age = EXTRACT(YEAR FROM AGE(CURRENT_DATE, birth_date))::INTEGER"
            );

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("✅ Обновлено возрастов: " + updated + " (за " + duration + " мс) в " + LocalDateTime.now());

        } catch (Exception e) {
            LOGGER.severe("❌ Ошибка обновления возраста: " + e.getMessage());
            e.printStackTrace();
        }
    }
}