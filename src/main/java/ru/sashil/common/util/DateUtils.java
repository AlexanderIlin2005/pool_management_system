package ru.sashil.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateUtils {
    /**
     * Пытается распарсить дату из различных форматов (DD.MM.YYYY, DD MM YYYY и т.д.)
     * Возвращает строку в формате YYYY-MM-DD для базы данных.
     */
    public static String normalizeDate(String input) {
        if (input == null || input.isEmpty()) return null;

        // Очищаем от лишних пробелов
        String cleaned = input.trim().replaceAll("\\s+", " ");

        // Заменяем разделители (точки, тире, слэши) на единый пробел для унификации
        String normalized = cleaned.replaceAll("[./\\-]", " ");

        String[] parts = normalized.split(" ");
        if (parts.length != 3) return null;

        try {
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);

            // Простая проверка на адекватность года
            if (year < 1900 || year > LocalDate.now().getYear()) return null;

            LocalDate date = LocalDate.of(year, month, day);
            return date.toString(); // Вернет YYYY-MM-DD
        } catch (Exception e) {
            return null;
        }
    }
}