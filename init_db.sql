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
CREATE TABLE pool.groups (
    id BIGSERIAL PRIMARY KEY,
    number INTEGER UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    capacity INTEGER CHECK (capacity BETWEEN 1 AND 50),
    pool_id BIGINT REFERENCES pool.pools(id),

    -- Внешний ключ на администратора с ролью COACH
    -- ON DELETE SET NULL означает: если тренера удалят из admin_users,
    -- то в группе поле trainer_id просто станет пустым (NULL), но группа не удалится.
    trainer_id BIGINT REFERENCES pool.admin_users(id) ON DELETE SET NULL,

    -- Поля для 7 дней недели
    day_1_start TIME, day_1_end TIME,
    day_2_start TIME, day_2_end TIME,
    day_3_start TIME, day_3_end TIME,
    day_4_start TIME, day_4_end TIME,
    day_5_start TIME, day_5_end TIME,
    day_6_start TIME, day_6_end TIME,
    day_7_start TIME, day_7_end TIME,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pool.group_children (
    group_id BIGINT REFERENCES pool.groups(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, child_id)
);

CREATE TABLE IF NOT EXISTS pool.document_versions (
    id BIGSERIAL PRIMARY KEY,
    doc_type VARCHAR(50) NOT NULL CHECK (doc_type IN ('CONTRACT', 'CONSENT', 'RULES', 'RECEIPT')),
    file_name VARCHAR(255) NOT NULL, -- Имя файла в MinIO (UUID.pdf)
    original_name VARCHAR(255),      -- Оригинальное имя файла при загрузке
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT FALSE,
    admin_id BIGINT REFERENCES pool.admin_users(id)
);

-- Таблица занятий (генерируется автоматически или вручную)
CREATE TABLE IF NOT EXISTS pool.pool_lessons (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT REFERENCES pool.groups(id) ON DELETE CASCADE,
    lesson_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_cancelled BOOLEAN DEFAULT FALSE, -- Отмена занятия (праздник/каникулы)
    cancellation_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(group_id, lesson_date, start_time)
);

-- Таблица посещаемости
CREATE TABLE IF NOT EXISTS pool.attendance (
    id BIGSERIAL PRIMARY KEY,
    lesson_id BIGINT REFERENCES pool.pool_lessons(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'SICK', 'EXCUSED')),
    marked_by BIGINT REFERENCES pool.admin_users(id),
    marked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    comment TEXT,
    UNIQUE(lesson_id, child_id)
);

-- Государственные праздники РФ
CREATE TABLE IF NOT EXISTS pool.holidays (
    id BIGSERIAL PRIMARY KEY,
    holiday_date DATE NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

-- Школьные каникулы (диапазоны дат)
CREATE TABLE IF NOT EXISTS pool.school_vacations (
    id BIGSERIAL PRIMARY KEY,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT check_dates CHECK (end_date >= start_date)
);

-- Таблица для отслеживания отправленных уведомлений
CREATE TABLE IF NOT EXISTS pool.notification_log (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES pool.parents(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    notification_type VARCHAR(50) NOT NULL, -- 'TOMORROW', 'TODAY', 'CANCELLED', 'TIME_CHANGED'
    lesson_date DATE NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(parent_id, child_id, notification_type, lesson_date)
);

CREATE TABLE IF NOT EXISTS pool.broadcast_messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT REFERENCES pool.admin_users(id), -- Кто отправил (админ или тренер)
    target_type VARCHAR(20) NOT NULL, -- 'ALL' (всем) или 'GROUP' (конкретной группе)
    target_group_id BIGINT, -- ID группы, если target_type = 'GROUP'
    message_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, ERROR
    sent_count INT DEFAULT 0 -- Сколько родителей получили сообщение
);

CREATE TABLE IF NOT EXISTS pool.skill_change_notifications (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES pool.parents(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    old_skill VARCHAR(50) NOT NULL,
    new_skill VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

-- Таблица для хранения справок от родителей
CREATE TABLE IF NOT EXISTS pool.certificates (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES pool.parents(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    file_url VARCHAR(500) NOT NULL, -- Ссылка на файл в MinIO
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE, -- Прочитана ли админом/тренером
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED_SICK', 'APPROVED_EXCUSED', 'REJECTED')),
    date_from DATE, -- Дата начала действия справки
    date_to DATE,   -- Дата окончания действия справки
    processed_by BIGINT REFERENCES pool.admin_users(id) -- Кто обработал справку
);

-- Индекс для быстрого поиска непрочитанных
CREATE INDEX idx_certificates_is_read ON pool.certificates(is_read);

-- Индекс для быстрого поиска неотправленных сообщений
CREATE INDEX IF NOT EXISTS idx_broadcast_status ON pool.broadcast_messages(status);

-- Добавляем флаг отключения регулярных уведомлений в таблицу родителей
ALTER TABLE pool.parents ADD COLUMN IF NOT EXISTS notify_regular BOOLEAN DEFAULT TRUE;

-- Индекс для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_notification_log_parent_date ON pool.notification_log(parent_id, lesson_date);

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_parents_vk_id ON pool.parents(vk_id);
CREATE INDEX IF NOT EXISTS idx_children_parent_id ON pool.children(parent_id);

-- Индекс для быстрого поиска активного документа каждого типа
CREATE UNIQUE INDEX idx_active_doc_type ON pool.document_versions(doc_type) WHERE is_active = TRUE;

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_pool_lessons_date ON pool.pool_lessons(lesson_date);
CREATE INDEX IF NOT EXISTS idx_pool_lessons_group_date ON pool.pool_lessons(group_id, lesson_date);
CREATE INDEX IF NOT EXISTS idx_attendance_lesson ON pool.attendance(lesson_id);
CREATE INDEX IF NOT EXISTS idx_holidays_date ON pool.holidays(holiday_date);

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