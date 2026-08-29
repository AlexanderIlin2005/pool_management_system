#!/bin/bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔═══════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Установка MinIO (Homebrew)        ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════╝${NC}"

# Определяем пути
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MINIO_DIR="$SCRIPT_DIR/minio"
MINIO_DATA="$MINIO_DIR/data"
MINIO_LOG="$MINIO_DIR/minio.log"
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001

# Убеждаемся, что MinIO установлен через Homebrew
if ! command -v minio &> /dev/null; then
    echo -e "${YELLOW}📦 MinIO не найден. Устанавливаю через Homebrew...${NC}"
    brew install minio/stable/minio
fi

if ! command -v mc &> /dev/null; then
    echo -e "${YELLOW}📦 MinIO Client не найден. Устанавливаю через Homebrew...${NC}"
    brew install minio/stable/mc
fi

# Создаем папку для данных и логов
mkdir -p "$MINIO_DATA"

# Запускаем MinIO
echo -e "${YELLOW}🚀 Запуск MinIO на порту $MINIO_PORT...${NC}"

# Убиваем старый процесс, если есть
pkill -f "minio.*server.*$MINIO_DATA" 2>/dev/null || true
sleep 1

nohup minio server "$MINIO_DATA" \
    --address ":$MINIO_PORT" \
    --console-address ":$MINIO_CONSOLE_PORT" \
    > "$MINIO_LOG" 2>&1 &

sleep 3

# Проверяем запуск
if curl -s "http://localhost:$MINIO_PORT/minio/health/ready" > /dev/null; then
    echo -e "${GREEN}✅ MinIO запущен успешно${NC}"
    echo -e "   Консоль: http://localhost:$MINIO_CONSOLE_PORT"
    echo -e "   Логин: minioadmin"
    echo -e "   Пароль: minioadmin123"
else
    echo -e "${RED}❌ MinIO не отвечает. Проверьте лог: $MINIO_LOG${NC}"
    if [ -f "$MINIO_LOG" ]; then
        tail -n 20 "$MINIO_LOG"
    fi
    exit 1
fi

# Настраиваем mc с фиксированными ключами
mc alias set local "http://localhost:$MINIO_PORT" minioadmin minioadmin123 2>/dev/null || true

# Создаем bucket для медицинских справок
echo -e "${YELLOW}📁 Создание bucket 'medical-certificates'...${NC}"
mc mb local/medical-certificates --ignore-existing 2>/dev/null || true
mc anonymous set download local/medical-certificates 2>/dev/null || true
echo -e "${GREEN}✅ Bucket 'medical-certificates' готов${NC}"

# Создаем bucket для документов админки
echo -e "${YELLOW}📁 Создание bucket 'pool-documents'...${NC}"
mc mb local/pool-documents --ignore-existing 2>/dev/null || true
mc anonymous set download local/pool-documents 2>/dev/null || true
echo -e "${GREEN}✅ Bucket 'pool-documents' готов${NC}"

# Обновляем .env с фиксированными ключами
cat > "$SCRIPT_DIR/.env" << END
# PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pool_db
DB_USER=pool_admin
DB_PASSWORD=pool_admin_password

# VK Bot
VK_BOT_TOKEN=vk1.a.bXIAnXw9FrPinG_bY0yUkC_whKlIhct33A_d9Sgqpk3v0cE9C5yDFRYMWu9BeIx6fE3gj8VxYXWx8kWpnB7UU7kiTWLF6_uoniV05LtZI65vPmtiBC_M4FO6gHmKqoHl-bG56dDnf70j1IfDt_i7M3nxs0KgaK45Jpn0K-36OmXygM5aMQ_fbD0FhXXEP_sAy_xcjUnyFG-eK-qjF3aKcA

# Spring
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# Long Poll
LONG_POLL_TIMEOUT=25
DB_MAX_RETRIES=5
DB_RETRY_DELAY=5

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
MINIO_BUCKET=medical-certificates
MINIO_DOCS_BUCKET=pool-documents
END

echo -e "${GREEN}✅ .env обновлен${NC}"