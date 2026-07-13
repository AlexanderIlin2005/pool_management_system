package ru.sashil.admin.model;

/**
 * Навык плавания ребенка.
 * Значения должны точно совпадать с ENUM в PostgreSQL: pool.swimming_skill
 */
public enum SwimmingSkill {
    НЕ_УМЕЕТ("не умеет"),
    ДЕРЖИТСЯ_НA_ВОДЕ("держится на воде"),
    УВЕРЕННО_ПЛАВАЕТ("уверенно плавает");

    private final String dbValue;

    SwimmingSkill(String dbValue) {
        this.dbValue = dbValue;
    }

    /**
     * Возвращает точное значение, которое хранится в базе данных.
     * Используется при сохранении/чтении через JPA (EnumType.STRING).
     */
    @Override
    public String toString() {
        return dbValue;
    }
}