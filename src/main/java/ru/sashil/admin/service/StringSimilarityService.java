package ru.sashil.admin.service;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

@Service
public class StringSimilarityService {

    private final LevenshteinDistance levenshtein = new LevenshteinDistance();

    /**
     * Проверяет схожесть двух строк на основе расстояния Левенштейна.
     * @param s1 Первая строка
     * @param s2 Вторая строка
     * @param threshold Порог схожести от 0.0 до 1.0 (где 1.0 - полное совпадение)
     * @return true, если схожесть выше порога
     */
    public boolean isSimilar(String s1, String s2, double threshold) {
        if (s1 == null || s2 == null) return false;

        String str1 = s1.toLowerCase().trim();
        String str2 = s2.toLowerCase().trim();

        if (str1.equals(str2)) return true;

        int distance = levenshtein.apply(str1, str2);
        int maxLen = Math.max(str1.length(), str2.length());

        if (maxLen == 0) return true;

        double similarity = 1.0 - ((double) distance / maxLen);
        return similarity >= threshold;
    }
}