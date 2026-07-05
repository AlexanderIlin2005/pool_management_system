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
    age INTEGER,
    grade_number INTEGER CHECK (grade_number BETWEEN 1 AND 11),
    grade_name VARCHAR(50), -- Полное название класса
    skill pool.swimming_skill NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pool.admin_users (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'ACCOUNTANT', 'COACH'))
);

-- Таблица бассейнов
CREATE TABLE IF NOT EXISTS pool.pools (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL
);

-- Таблица групп
CREATE TABLE IF NOT EXISTS pool.groups (
    id BIGSERIAL PRIMARY KEY,
    number INTEGER UNIQUE NOT NULL,
    trainer_id BIGINT, -- Пока может быть null, позже свяжем с таблицей тренеров
    name VARCHAR(100) NOT NULL,
    day_of_week_1 INTEGER CHECK (day_of_week_1 BETWEEN 1 AND 7), -- 1=Пн, 7=Вс
    start_time_1 TIME NOT NULL,
    end_time_1 TIME NOT NULL,
    day_of_week_2 INTEGER CHECK (day_of_week_2 BETWEEN 1 AND 7),
    start_time_2 TIME,
    end_time_2 TIME,
    capacity INTEGER CHECK (capacity BETWEEN 1 AND 50),
    pool_id BIGINT REFERENCES pool.pools(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_parents_vk_id ON pool.parents(vk_id);
CREATE INDEX IF NOT EXISTS idx_children_parent_id ON pool.children(parent_id);

CREATE OR REPLACE FUNCTION pool.calculate_age()
RETURNS TRIGGER AS $$
BEGIN
    NEW.age := EXTRACT(YEAR FROM AGE(NEW.birth_date))::INTEGER;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER set_age
BEFORE INSERT OR UPDATE OF birth_date ON pool.children
FOR EACH ROW
EXECUTE FUNCTION pool.calculate_age();