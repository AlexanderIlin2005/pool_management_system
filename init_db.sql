-- Подключаемся к базе
\c pool_db;

-- Создание схемы
CREATE SCHEMA IF NOT EXISTS pool;

-- Таблица пользователей
CREATE TABLE IF NOT EXISTS pool.users (
    id BIGSERIAL PRIMARY KEY,
    vk_id BIGINT UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    parent_first_name VARCHAR(100),
    parent_last_name VARCHAR(100),
    birth_date DATE NOT NULL,
    grade VARCHAR(20),
    email VARCHAR(255),
    phone VARCHAR(20),
    group_id BIGINT,
    subscription_type VARCHAR(20) DEFAULT 'MONTHLY',
    is_active BOOLEAN DEFAULT true,
    consent_to_data_processing BOOLEAN DEFAULT false,
    consent_date DATE,
    medical_certificate_expiry DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Группы
CREATE TABLE IF NOT EXISTS pool.groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    max_participants INTEGER NOT NULL,
    current_participants INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Расписание
CREATE TABLE IF NOT EXISTS pool.schedule (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT REFERENCES pool.groups(id),
    weekday INTEGER NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Абонементы
CREATE TABLE IF NOT EXISTS pool.subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES pool.users(id),
    schedule_id BIGINT REFERENCES pool.schedule(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_sessions INTEGER,
    used_sessions INTEGER DEFAULT 0,
    remaining_sessions INTEGER,
    price DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    frozen_until DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Посещаемость
CREATE TABLE IF NOT EXISTS pool.attendance (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES pool.users(id),
    schedule_id BIGINT REFERENCES pool.schedule(id),
    attendance_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ATTENDED',
    is_excused BOOLEAN DEFAULT false,
    excuse_reason VARCHAR(255),
    medical_certificate_url VARCHAR(500),
    note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Медицинские справки
CREATE TABLE IF NOT EXISTS pool.medical_certificates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES pool.users(id),
    certificate_number VARCHAR(100),
    issue_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    file_url VARCHAR(500),
    is_verified BOOLEAN DEFAULT false,
    verified_by BIGINT,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON pool.attendance(user_id, attendance_date);
CREATE INDEX IF NOT EXISTS idx_attendance_schedule_date ON pool.attendance(schedule_id, attendance_date);
CREATE INDEX IF NOT EXISTS idx_schedule_group_weekday ON pool.schedule(group_id, weekday);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status ON pool.subscriptions(user_id, status);
CREATE INDEX IF NOT EXISTS idx_medical_certificates_user_expiry ON pool.medical_certificates(user_id, expiry_date);

-- Триггер для обновления updated_at
CREATE OR REPLACE FUNCTION pool.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON pool.users
    FOR EACH ROW EXECUTE FUNCTION pool.update_updated_at_column();

CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON pool.subscriptions
    FOR EACH ROW EXECUTE FUNCTION pool.update_updated_at_column();

CREATE TRIGGER update_attendance_updated_at BEFORE UPDATE ON pool.attendance
    FOR EACH ROW EXECUTE FUNCTION pool.update_updated_at_column();

CREATE TRIGGER update_medical_certificates_updated_at BEFORE UPDATE ON pool.medical_certificates
    FOR EACH ROW EXECUTE FUNCTION pool.update_updated_at_column();
