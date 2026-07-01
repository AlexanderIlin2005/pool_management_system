package ru.sashil.common.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class ConfigLoader {
    private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
    private static Map<String, String> config = null;

    public static synchronized Map<String, String> load() {
        if (config != null) {
            return config;
        }

        config = new HashMap<>();

        // 1. Пробуем загрузить из resources (application.properties)
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                LOGGER.info("📄 Загружаем application.properties из resources...");
                Properties props = new Properties();
                props.load(input);
                for (String key : props.stringPropertyNames()) {
                    config.put(key, props.getProperty(key));
                }
                LOGGER.info("✅ Загружено " + config.size() + " свойств из application.properties");
                return config;
            }
        } catch (Exception e) {
            LOGGER.warning("⚠️ Не удалось загрузить application.properties из resources: " + e.getMessage());
        }

        // 2. Пробуем из корня проекта (dev режим)
        try {
            Path path = Paths.get("application.properties");
            if (Files.exists(path)) {
                LOGGER.info("📄 Загружаем application.properties из файловой системы...");
                Properties props = new Properties();
                try (InputStream input = Files.newInputStream(path)) {
                    props.load(input);
                }
                for (String key : props.stringPropertyNames()) {
                    config.put(key, props.getProperty(key));
                }
                LOGGER.info("✅ Загружено " + config.size() + " свойств из файловой системы");
                return config;
            }
        } catch (Exception e) {
            LOGGER.warning("⚠️ Ошибка чтения application.properties: " + e.getMessage());
        }

        LOGGER.severe("❌ application.properties НЕ НАЙДЕН!");
        return config;
    }

    public static String get(String key) {
        if (config == null) {
            load();
        }
        return config.get(key);
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
