#!/bin/bash

# Устанавливаем имя выходного файла
OUTPUT_FILE="codebase.txt"

# --- Настройки для безопасной обработки путей с пробелами или спецсимволами ---
IFS=$'\n'
set -f
# ------------------------------------------------------------------------------

# 1. Создаем/перезаписываем файл вывода
> "$OUTPUT_FILE"

echo "Скрипт запущен в $(date)" >> "$OUTPUT_FILE"
echo "--------------------------------------------------" >> "$OUTPUT_FILE"

# 2. Основная логика: рекурсивный поиск файлов с фильтрацией

# Ищем все файлы (-type f) в текущей директории (.),
# ИСКЛЮЧАЯ (-not -name):
# - Самого скрипта
# - Файла вывода
# - Файлов с расширением .jar
# ИСКЛЮЧАЯ поддиректории (-path):
# - .gradle
# - build
# - gradle
find . -type f \
  -not -name "*.jar" \
  -not -path "*/.gradle/*" \
  -not -path "*/build/*" \
  -not -path "*/.git/*" \
  -not -path "*/gradle/*" \
  -print0 | while IFS= read -r -d '' file
do
    # Дополнительная проверка на скрипт и файл вывода
    if [[ "$file" == "./full_tree.sh" ]] || \
       [[ "$file" == "./$OUTPUT_FILE" ]] || \
       [[ "$file" =~ /\.gradle/ ]] || \
       [[ "$file" =~ /build/ ]] || \
       [[ "$file" =~ /gradle/ ]]; then
        continue
    fi

    # Вывод пути к файлу в выходной файл
    echo "" >> "$OUTPUT_FILE"
    echo "==================================================" >> "$OUTPUT_FILE"
    echo "Содержимое файла: $file" >> "$OUTPUT_FILE"
    echo "==================================================" >> "$OUTPUT_FILE"

    # Вывод содержимого файла (включая текстовые, XML, YAML и т.д.)
    cat "$file" >> "$OUTPUT_FILE"

done

# 3. Добавление уведомления об исключенных файлах
echo "" >> "$OUTPUT_FILE"
echo "--- Исключенные типы файлов и директории ---" >> "$OUTPUT_FILE"
echo "Файлы: .jar (двоичный формат)" >> "$OUTPUT_FILE"
echo "Директории: .gradle, build, gradle" >> "$OUTPUT_FILE"

# Показываем список пропущенных .jar файлов для справки
find . -type f -name "*.jar" -print | sed 's/^/Пропущен (jar): /' >> "$OUTPUT_FILE"

# Также можно показать, сколько файлов пропущено в исключенных директориях
echo "" >> "$OUTPUT_FILE"
echo "Файлы в исключенных директориях:" >> "$OUTPUT_FILE"
for dir in .gradle build gradle; do
    if [ -d "$dir" ]; then
        count=$(find "$dir" -type f | wc -l)
        echo "Директория $dir: $count файлов пропущено" >> "$OUTPUT_FILE"
    fi
done

echo "" >> "$OUTPUT_FILE"
echo "--------------------------------------------------" >> "$OUTPUT_FILE"
echo "Скрипт завершен." >> "$OUTPUT_FILE"

# Восстановление настроек
unset IFS
set +f

echo "✅ Результат (без .jar файлов и исключенных директорий) сохранен в файл: $OUTPUT_FILE"