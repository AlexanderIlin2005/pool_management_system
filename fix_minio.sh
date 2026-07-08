# 1. Убиваем все процессы minio
pkill -f minio
sleep 2

# 2. Удаляем системную папку с метаданными (это сбросит пользователей и бакеты)
rm -rf ./minio/data/.minio.sys

# 3. Запускаем minio с ЯВНЫМ указанием новых root-ключей через env vars
export MINIO_ROOT_USER=minioadmin
export MINIO_ROOT_PASSWORD=minioadmin123

nohup minio server ./minio/data \
    --address ":9000" \
    --console-address ":9001" \
    > ./minio/minio.log 2>&1 &

# Ждем запуска
sleep 3

# 4. Проверяем, что сервер жив
curl -s http://localhost:9000/minio/health/ready && echo "✅ MinIO готов" || echo "❌ Ошибка"

# 5. Настраиваем mc клиент ПОД НОВЫЕ КЛЮЧИ
mc alias set local http://localhost:9000 minioadmin minioadmin123

# 6. Создаем бакеты заново (так как мы удалили .minio.sys)
mc mb local/medical-certificates
mc mb local/pool-documents
mc anonymous set download local/medical-certificates
mc anonymous set download local/pool-documents

echo "✅ Готово! Теперь можно запускать админку."