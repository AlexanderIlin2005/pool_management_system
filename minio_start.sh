#!/bin/bash
set -e

MINIO_DIR="./minio"
MINIO_BIN="$MINIO_DIR/minio"
MINIO_DATA="$MINIO_DIR/data"
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001

# Определяем путь к бинарнику
if [ -f "$MINIO_DIR/minio" ]; then
    MINIO_BIN="$MINIO_DIR/minio"
elif command -v minio &> /dev/null; then
    MINIO_BIN="minio"
else
    echo "❌ MinIO не установлен. Запустите ./minio_setup.sh или установите через brew install minio/stable/minio"
    exit 1
fi

echo "🚀 Запуск MinIO..."

# Останавливаем старый процесс, если есть
pkill -f "minio.*--address :$MINIO_PORT" 2>/dev/null || true
sleep 1

if pgrep -f "minio.*--address :$MINIO_PORT" > /dev/null; then
    echo "✅ MinIO уже запущен"
else
    # Создаем директорию данных, если нет
    mkdir -p "$MINIO_DATA"

    # Явно задаем ключи через переменные окружения
    export MINIO_ROOT_USER=minioadmin
    export MINIO_ROOT_PASSWORD=minioadmin123

    nohup "$MINIO_BIN" server "$MINIO_DATA" \
    --address ":$MINIO_PORT" \
    --console-address ":$MINIO_CONSOLE_PORT" \
    --compat \
    > "$MINIO_DIR/minio.log" 2>&1 &

    sleep 3

    if curl -s "http://localhost:$MINIO_PORT/minio/health/ready" > /dev/null; then
        echo "✅ MinIO запущен (консоль: http://localhost:$MINIO_CONSOLE_PORT)"
        echo "   Логин: minioadmin"
        echo "   Пароль: minioadmin123"
    else
        echo "⚠️ MinIO запустился, но пока не отвечает. Проверьте лог: $MINIO_DIR/minio.log"
    fi
fi