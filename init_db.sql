\c pool_db;

-- Создаем схему, если её нет
CREATE SCHEMA IF NOT EXISTS pool;

-- Убеждаемся, что у pool_admin есть все права на схему
GRANT ALL ON SCHEMA pool TO pool_admin;

-- Тип данных для навыков плавания
-- Используем DO блок, чтобы игнорировать ошибку, если тип уже существует
DO $$ BEGIN
    CREATE TYPE pool.swimming_skill AS ENUM ('не умеет', 'держится на воде', 'уверенно плавает');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Таблица родителей (основные пользователи)
CREATE TABLE IF NOT EXISTS pool.parents (
    id BIGSERIAL PRIMARY KEY,
    vk_id BIGINT UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100), -- Отчество
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица детей
CREATE TABLE IF NOT EXISTS pool.children (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES pool.parents(id) ON DELETE CASCADE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    birth_date DATE NOT NULL,
    -- Возраст считаем динамически через представление или хранимую функцию,
    -- так как GENERATED ALWAYS AS STORED требует триггеров или сложных настроек в старых PG,
    -- но в PG 12+ это работает. Оставим как есть, если версия позволяет.
    age INTEGER GENERATED ALWAYS AS (EXTRACT(YEAR FROM AGE(birth_date))::INTEGER) STORED,
    grade_number INTEGER CHECK (grade_number BETWEEN 1 AND 11),
    grade_name VARCHAR(50), -- Полное название класса
    skill pool.swimming_skill NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    phone VARCHAR(20);
);

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_parents_vk_id ON pool.parents(vk_id);
CREATE INDEX IF NOT EXISTS idx_children_parent_id ON pool.children(parent_id);