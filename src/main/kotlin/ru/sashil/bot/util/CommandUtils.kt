package ru.sashil.bot.util

/**
 * Утилита для работы с командами бота
 */
object CommandUtils {

    /**
     * Проверяет, является ли текст командой пропуска поля
     * Поддерживает множество вариантов:
     * - Пробел (один или несколько)
     * - Тире разных видов: -, —, –
     * - Нижнее подчеркивание: _
     * - Цифра 0
     * - Буква О (кириллица и латиница в разных регистрах)
     * - Слово "skip" (англ.)
     * - Слово "пропустить" (рус.)
     */
    fun isSkipCommand(text: String?): Boolean {
        if (text == null) return false

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true // Пустая строка - тоже пропуск

        // Проверяем по списку разрешенных символов для пропуска
        return when (trimmed) {
            // Одиночные символы
            "-", "—", "–", "_", "0",
                // Буква О в разных вариантах
            "о", "О", "o", "O",
                // Слова
            "skip", "SKIP", "Skip",
            "пропустить", "Пропустить", "ПРОПУСТИТЬ"
                -> true
            else -> false
        }
    }

    /**
     * Проверяет, является ли текст командой отмены
     */
    fun isCancelCommand(text: String?): Boolean {
        if (text == null) return false
        val normalized = text.trim().lowercase()
        return normalized == "отмена" ||
                normalized == "нет" ||
                normalized == "no" ||
                normalized == "n" ||
                normalized == "cancel"
    }

    /**
     * Нормализует текст команды для сравнения
     */
    fun normalize(text: String?): String {
        return text?.trim()?.lowercase() ?: ""
    }
}