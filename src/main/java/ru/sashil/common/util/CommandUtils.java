package ru.sashil.common.util;

public class CommandUtils {
    /**
     * Очищает строку от всех символов, кроме букв русского и английского алфавита.
     * Приводит к нижнему регистру.
     * Пример: " От-Ме_На! " -> "отмена"
     */
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // Оставляем только буквы (\\p{L} поддерживает Unicode, включая кириллицу)
        String cleaned = input.replaceAll("[^\\p{L}]", "");
        return cleaned.toLowerCase();
    }
}