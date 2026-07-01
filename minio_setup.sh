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

MINIO_DATA="./minio/data"
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

# Создаем папку для данных
mkdir -p "$MINIO_DATA"

# Генерируем случайный пароль
MINIO_SECRET=$(openssl rand -base64 24 | tr -d "=+/" | cut -c1-32)

# Запускаем MinIO
echo -e "${YELLOW}🚀 Запуск MinIO на порту $MINIO_PORT...${NC}"
if pgrep -f "minio.*--address :$MINIO_PORT" > /dev/null; then
    echo -e "${GREEN}✅ MinIO уже запущен${NC}"
else
    nohup minio server "$MINIO_DATA" \
        --address ":$MINIO_PORT" \
        --console-address ":$MINIO_CONSOLE_PORT" \
        > "$MINIO_DATA/../minio.log" 2>&1 &
    sleep 3
    echo -e "${GREEN}✅ MinIO запущен (консоль: http://localhost:$MINIO_CONSOLE_PORT)${NC}"
fi

# Проверяем
if ! curl -s "http://localhost:$MINIO_PORT/minio/health/ready" > /dev/null; then
    echo -e "${RED}❌ MinIO не отвечает. Проверьте лог: ./minio/minio.log${NC}"
    cat "./minio/minio.log" 2>/dev/null || echo "Лог не найден"
    exit 1
fi

# Настраиваем mc
mc alias set local "http://localhost:$MINIO_PORT" minioadmin "$MINIO_SECRET" 2>/dev/null || true

# Создаем bucket
echo -e "${YELLOW}📁 Создание bucket 'medical-certificates'...${NC}"
mc mb local/medical-certificates --ignore-existing 2>/dev/null || true
mc anonymous set download local/medical-certificates 2>/dev/null || true

echo -e "${GREEN}✅ Bucket 'medical-certificates' готов${NC}"

# Обновляем .env
echo -e "${YELLOW}🔐 Обновление .env...${NC}"
cat >> .env << END
MINIO_ENDPOINT=http://localhost:$MINIO_PORT
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=$MINIO_SECRET
MINIO_BUCKET=medical-certificates
END

echo -e "${GREEN}✅ .env обновлен${NC}"
echo -e "${BLUE}📋 Сохраните эти данные:${NC}"
echo -e "   MINIO_ACCESS_KEY: minioadmin"
echo -e "   MINIO_SECRET_KEY: $MINIO_SECRET"
echo -e "   Консоль: http://localhost:$MINIO_CONSOLE_PORT"
