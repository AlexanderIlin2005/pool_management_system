#!/bin/bash

set -e

MINIO_DIR="./minio"
MINIO_BIN="$MINIO_DIR/minio"
MINIO_DATA="$MINIO_DIR/data"
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001

if [ ! -f "$MINIO_BIN" ]; then
    echo "❌ MinIO не установлен. Запустите ./minio_setup.sh"
    exit 1
fi

echo "🚀 Запуск MinIO..."
if pgrep -f "minio.*--address :$MINIO_PORT" > /dev/null; then
    echo "✅ MinIO уже запущен"
else
    nohup "$MINIO_BIN" server "$MINIO_DATA" \
        --address ":$MINIO_PORT" \
        --console-address ":$MINIO_CONSOLE_PORT" \
        > "$MINIO_DIR/minio.log" 2>&1 &
    sleep 2
    echo "✅ MinIO запущен (консоль: http://localhost:$MINIO_CONSOLE_PORT)"
fi
