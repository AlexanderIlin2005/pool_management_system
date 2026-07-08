#!/bin/bash
set -e

MINIO_DIR="./minio"
MINIO_DATA="$MINIO_DIR/data"
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001

echo "🚀 Запуск MinIO..."

# Останавливаем старый процесс
pkill -f "minio.*server" 2>/dev/null || true
sleep 2

# Создаем директорию данных
mkdir -p "$MINIO_DATA"

# ВАЖНО: Используем новые переменные для современных версий MinIO
export MINIO_ROOT_USER=minioadmin
export MINIO_ROOT_PASSWORD=minioadmin123

# Запускаем
nohup minio server "$MINIO_DATA" \
    --address ":$MINIO_PORT" \
    --console-address ":$MINIO_CONSOLE_PORT" \
    > "$MINIO_DIR/minio.log" 2>&1 &

sleep 3

if curl -s "http://localhost:$MINIO_PORT/minio/health/ready" > /dev/null; then
    echo "✅ MinIO запущен успешно"
    echo "   Логин: minioadmin"
    echo "   Пароль: minioadmin123"
else
    echo "❌ Ошибка запуска. Лог:"
    cat "$MINIO_DIR/minio.log"
fi