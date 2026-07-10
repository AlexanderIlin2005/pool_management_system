package ru.sashil.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.AdminUser;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AuditLogService {

    private static final String LOG_DIR = "./data/logs";
    private static final String LOG_FILE = "audit.log";
    // ИСПРАВЛЕНИЕ: Формат даты изменен на dd-MM-yyyy
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditLogService() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать директорию для логов", e);
        }
    }

    /**
     * Запись значимого действия
     */
    public void log(String action, AdminUser actor, String details) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String actorName = actor != null ? actor.getFullName() + " (" + actor.getLogin() + ")" : "System";

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("timestamp", timestamp);
        entry.put("actor", actorName);
        entry.put("action", action);
        entry.put("details", details);

        try {
            String line = mapper.writeValueAsString(entry);
            Files.write(Paths.get(LOG_DIR, LOG_FILE),
                    (line + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Ошибка записи аудиторского лога: " + e.getMessage());
        }
    }

    /**
     * Получение логов с фильтрацией и сортировкой
     */
    public List<Map<String, String>> getLogs(String search, String sortBy, int limit) {
        Path path = Paths.get(LOG_DIR, LOG_FILE);
        if (!Files.exists(path)) return Collections.emptyList();

        try (Stream<String> lines = Files.lines(path)) {
            List<Map<String, String>> result = lines
                    .filter(line -> !line.isBlank())
                    .map(line -> {
                        try {
                            return mapper.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (search != null && !search.isEmpty()) {
                String lowerSearch = search.toLowerCase();
                result = result.stream()
                        .filter(m -> m.values().stream()
                                .anyMatch(v -> v != null && v.toString().toLowerCase().contains(lowerSearch)))
                        .collect(Collectors.toList());
            }

            if ("date_asc".equals(sortBy)) {
                result.sort(Comparator.comparing(m -> m.getOrDefault("timestamp", "")));
            } else {
                result.sort(Comparator.comparing(m -> m.getOrDefault("timestamp", ""), Comparator.reverseOrder()));
            }

            return result.stream().limit(limit).collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Очистка логов с сохранением записи о ротации
     */
    public void clearLogs(AdminUser admin) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String rotationEntry = String.format(
                "{\"timestamp\":\"%s\",\"actor\":\"%s (%s)\",\"action\":\"LOG_ROTATION\",\"details\":\"Логи очищены администратором\"}%n",
                timestamp, admin.getFullName(), admin.getLogin()
        );

        try {
            Files.writeString(Paths.get(LOG_DIR, LOG_FILE), rotationEntry);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка очистки логов", e);
        }
    }
}