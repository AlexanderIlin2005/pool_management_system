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
            for (SwimmingSkill skill : SwimmingSkill.values()) {
                if (skill.getDbValue().equals(dbData)) {
                    return skill;
                }
            }
            throw new IllegalArgumentException("Unknown swimming skill: " + dbData);
        }
    }
}