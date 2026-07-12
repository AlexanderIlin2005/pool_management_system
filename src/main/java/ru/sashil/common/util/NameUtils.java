package ru.sashil.common.util;

public class NameUtils {

    /**
     * Преобразует полное ФИО в формат "Фамилия И.О."
     * Пример: "Иванов Иван Иванович" -> "Иванов И.И."
     */
    public static String toInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";

        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) return fullName;

        StringBuilder sb = new StringBuilder(parts[0]); // Фамилия

        for (int i = 1; i < parts.length && i <= 2; i++) {
            char initial = Character.toUpperCase(parts[i].charAt(0));
            sb.append(" ").append(initial).append(".");
        }

        return sb.toString();
    }
}