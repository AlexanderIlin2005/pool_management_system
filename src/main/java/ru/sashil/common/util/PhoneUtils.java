package ru.sashil.common.util;

public class PhoneUtils {
    public static String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.isEmpty()) return null;

        // Оставляем только цифры
        String cleaned = rawPhone.replaceAll("\\D", "");

        // Логика обработки российских номеров
        if (cleaned.length() == 11) {
            if (cleaned.startsWith("8")) {
                cleaned = "+7" + cleaned.substring(1);
            } else if (cleaned.startsWith("7")) {
                cleaned = "+" + cleaned;
            } else {
                return null;
            }
        } else if (cleaned.length() == 10) {
            cleaned = "+7" + cleaned;
        } else {
            return null;
        }

        // Финальная проверка: должно быть ровно 12 символов (+7 и 10 цифр)
        if (cleaned.length() != 12 || !cleaned.startsWith("+7")) {
            return null;
        }

        return cleaned;
    }
}