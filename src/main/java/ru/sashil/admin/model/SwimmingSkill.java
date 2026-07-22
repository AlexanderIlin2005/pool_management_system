package ru.sashil.admin.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum SwimmingSkill {
    НЕ_УМЕЕТ("не умеет"),
    ДЕРЖИТСЯ_НА_ВОДЕ("держится на воде"),
    УВЕРЕННО_ПЛАВАЕТ("уверенно плавает");

    private final String dbValue;

    SwimmingSkill(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    @Converter
    public static class SwimmingSkillConverter implements AttributeConverter<SwimmingSkill, String> {
        @Override
        public String convertToDatabaseColumn(SwimmingSkill attribute) {
            return attribute != null ? attribute.getDbValue() : null;
        }

        @Override
        public SwimmingSkill convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;

            // Нормализуем: удаляем лишние пробелы, приводим к нижнему регистру
            String normalized = dbData.trim().toLowerCase();

            for (SwimmingSkill skill : SwimmingSkill.values()) {
                if (skill.getDbValue().equals(normalized)) {
                    return skill;
                }
            }

            // Если все равно не нашли, пробуем искать по частичному совпадению
            for (SwimmingSkill skill : SwimmingSkill.values()) {
                if (skill.getDbValue().contains(normalized) || normalized.contains(skill.getDbValue())) {
                    return skill;
                }
            }

            // Если ничего не подошло, логируем и возвращаем default
            System.err.println("Unknown swimming skill: " + dbData + ". Using default: " + SwimmingSkill.НЕ_УМЕЕТ.getDbValue());
            return SwimmingSkill.НЕ_УМЕЕТ;
        }
    }
}