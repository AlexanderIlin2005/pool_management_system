\c pool_db;

-- Создаем схему, если её нет
CREATE SCHEMA IF NOT EXISTS pool;

-- Убеждаемся, что у pool_admin есть все права на схему
GRANT ALL ON SCHEMA pool TO pool_admin;

-- ===== НАСТРОЙКА ПРАВ ДОСТУПА =====
-- Даем права пользователю pool_admin на все объекты в схеме pool
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA pool TO pool_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA pool TO pool_admin;
GRANT USAGE ON SCHEMA pool TO pool_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA pool GRANT ALL PRIVILEGES ON TABLES TO pool_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA pool GRANT ALL PRIVILEGES ON SEQUENCES TO pool_admin;

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    phone VARCHAR(20),
    notify_regular BOOLEAN DEFAULT TRUE
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    certificate_received BOOLEAN DEFAULT FALSE
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

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    min_age INTEGER CHECK (min_age BETWEEN 6 AND 18),
    max_age INTEGER CHECK (max_age BETWEEN 6 AND 18),
    skill_1 VARCHAR(50),
    skill_2 VARCHAR(50),
    subscription_type VARCHAR(50)
);

ALTER TABLE pool.groups ADD CONSTRAINT chk_subscription_type CHECK (
    subscription_type IS NULL OR
    subscription_type IN ('ONCE_PER_WEEK', 'TWICE_PER_WEEK', 'INDIVIDUAL', 'FAMILY', 'AQUA_AEROBICS')
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
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED_SICK', 'APPROVED_EXCUSED', 'REJECTED', 'APPROVED')),
    date_from DATE, -- Дата начала действия справки
    date_to DATE,   -- Дата окончания действия справки
    processed_by BIGINT REFERENCES pool.admin_users(id), -- Кто обработал справку
    comment TEXT
);

ALTER TABLE pool.groups
ADD CONSTRAINT chk_skills_valid CHECK (
    (skill_1 IS NULL AND skill_2 IS NULL) OR          -- Ничего не выбрано
    (skill_2 IS NULL AND skill_1 IS NOT NULL) OR      -- Выбран только один навык (в skill_1)
    (skill_1 IS NOT NULL AND skill_2 IS NOT NULL      -- Выбрано два навыка
     AND skill_1 != skill_2
     AND NOT (skill_1 = 'не умеет' AND skill_2 = 'уверенно плавает')
     AND NOT (skill_1 = 'уверенно плавает' AND skill_2 = 'не умеет'))
);

-- Таблица заявок на вступление в группу
CREATE TABLE IF NOT EXISTS pool.group_join_requests (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES pool.parents(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    group_id BIGINT REFERENCES pool.groups(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    admin_comment TEXT, -- Комментарий админа при отклонении или подтверждении
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Индекс для быстрого поиска непрочитанных/необработанных заявок
CREATE INDEX idx_group_requests_status ON pool.group_join_requests(status);

-- Таблица уведомлений о статусе заявки (для отправки через бота)
CREATE TABLE IF NOT EXISTS pool.join_request_notifications (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT REFERENCES pool.group_join_requests(id) ON DELETE CASCADE,
    parent_vk_id BIGINT NOT NULL,
    message_text TEXT NOT NULL,
    is_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

-- Таблица для хранения оплат
CREATE TABLE IF NOT EXISTS pool.payments (
    id BIGSERIAL PRIMARY KEY,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    month_year DATE NOT NULL, -- Первое число месяца (например, 2026-09-01)
    is_paid BOOLEAN DEFAULT FALSE,
    paid_at TIMESTAMP,
    amount DECIMAL(10, 2) DEFAULT 0.00,
    total_paid DECIMAL(10, 2) DEFAULT 0.00,
    amount_history JSONB DEFAULT '[]'::jsonb,
    amount_change_comment TEXT,
    payment_method VARCHAR(50), -- 'CASH', 'BANK', 'QR', 'RECEIPT'
    receipt_file_url VARCHAR(500), -- Ссылка на файл квитанции в MinIO
    receipt_original_name VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
    verified_by BIGINT REFERENCES pool.admin_users(id),
    verified_at TIMESTAMP,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(child_id, month_year)
);

-- Таблица для уведомлений об оплате (для бота)
CREATE TABLE IF NOT EXISTS pool.payment_notifications (
    id BIGSERIAL PRIMARY KEY,
    parent_vk_id BIGINT NOT NULL,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    month_year DATE NOT NULL,
    message_text TEXT NOT NULL,
    notification_type VARCHAR(20) NOT NULL CHECK (notification_type IN ('REMINDER', 'OVERDUE', 'RECEIPT_CONFIRMED', 'RECEIPT_REJECTED')),
    is_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

-- Таблица для глобальных настроек
CREATE TABLE IF NOT EXISTS pool.settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(50) UNIQUE NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES pool.admin_users(id)
);

-- Вставляем настройку по умолчанию
INSERT INTO pool.settings (setting_key, setting_value)
VALUES ('DEFAULT_PAYMENT_AMOUNT', '4000.00')
ON CONFLICT (setting_key) DO NOTHING;

-- Таблица уведомлений об изменении данных ребенка
CREATE TABLE IF NOT EXISTS pool.child_update_notifications (
    id BIGSERIAL PRIMARY KEY,
    parent_vk_id BIGINT NOT NULL,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    message_text TEXT NOT NULL,
    is_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);


-- Таблица для уведомлений о пропусках занятий
CREATE TABLE IF NOT EXISTS pool.absence_notifications (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES pool.parents(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES pool.children(id) ON DELETE CASCADE,
    absence_type VARCHAR(20) NOT NULL CHECK (absence_type IN ('SICK', 'UNWELL', 'OTHER')),
    message TEXT,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'READ')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_by BIGINT REFERENCES pool.admin_users(id),
    -- Добавляем колонки для хранения справки в absence_notifications
    certificate_url VARCHAR(500),
    certificate_file_name VARCHAR(255)
);

-- Таблица для сообщений между родителями и тренерами/администраторами
CREATE TABLE IF NOT EXISTS pool.messages (
    id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL,           -- VK ID родителя или ID админа/тренера
    from_user_type VARCHAR(20) NOT NULL,    -- 'PARENT', 'ADMIN', 'COACH'
    to_user_id BIGINT,                      -- ID админа/тренера (если null - всем админам)
    to_user_type VARCHAR(20) NOT NULL,      -- 'ADMIN', 'COACH', 'PARENT'
    child_id BIGINT REFERENCES pool.children(id) ON DELETE SET NULL,
    group_id BIGINT REFERENCES pool.groups(id) ON DELETE SET NULL,
    message_text TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'READ', 'REPLIED')),
    parent_message_id BIGINT,               -- Ссылка на исходное сообщение при ответе
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    replied_at TIMESTAMP,
    sent_at TIMESTAMP
);


ALTER TABLE pool.payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE pool.payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID', 'PARTIAL'));

ALTER TABLE pool.messages DROP CONSTRAINT IF EXISTS messages_status_check;
ALTER TABLE pool.messages ADD CONSTRAINT messages_status_check CHECK (status IN ('PENDING', 'READ', 'REPLIED', 'SENT'));

-- Индексы для быстрого поиска
CREATE INDEX idx_messages_status ON pool.messages(status);
CREATE INDEX idx_messages_from_user ON pool.messages(from_user_id);
CREATE INDEX idx_messages_to_user ON pool.messages(to_user_id);
CREATE INDEX idx_messages_child ON pool.messages(child_id);
CREATE INDEX idx_messages_group ON pool.messages(group_id);
CREATE INDEX idx_messages_created ON pool.messages(created_at DESC);

-- Индексы для быстрого поиска
CREATE INDEX idx_absence_notifications_status ON pool.absence_notifications(status);
CREATE INDEX idx_absence_notifications_child ON pool.absence_notifications(child_id);
CREATE INDEX idx_absence_notifications_parent ON pool.absence_notifications(parent_id);


CREATE INDEX IF NOT EXISTS idx_children_certificate_received ON pool.children(certificate_received);

CREATE INDEX idx_child_update_notifications_pending ON pool.child_update_notifications(is_sent);
CREATE INDEX idx_child_update_notifications_parent ON pool.child_update_notifications(parent_vk_id);

-- Индексы для быстрого поиска
CREATE INDEX idx_payments_child_month ON pool.payments(child_id, month_year);
CREATE INDEX idx_payments_status ON pool.payments(status);
CREATE INDEX idx_payment_notifications_pending ON pool.payment_notifications(is_sent);
CREATE INDEX idx_payment_notifications_parent ON pool.payment_notifications(parent_vk_id);

CREATE INDEX idx_join_notif_pending ON pool.join_request_notifications(is_sent);

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