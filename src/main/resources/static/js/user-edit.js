document.addEventListener('DOMContentLoaded', function() {
    const passwordContainer = document.getElementById('passwordContainer');
    const passwordField = document.getElementById('passwordField');
    const toggleBtn = document.getElementById('togglePasswordBtn');
    const downloadBtn = document.getElementById('downloadBtn');
    const saveBtn = document.getElementById('saveBtn');
    const downloadFlag = document.getElementById('downloadFlag');

    // 1. Логика скрытия/раскрытия пароля с подтверждением
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function() {
            const isHidden = passwordContainer.style.display === 'none';

            if (isHidden) {
                // Запрашиваем подтверждение перед открытием
                if (confirm("Вы действительно хотите изменить пароль? Это действие потребует ввода нового пароля.")) {
                    passwordContainer.style.display = 'block';
                    downloadBtn.style.display = 'inline-block'; // Показываем кнопку скачивания
                    passwordField.focus();
                    this.textContent = 'Скрыть поле пароля';
                }
            } else {
                // Скрываем и очищаем
                passwordContainer.style.display = 'none';
                downloadBtn.style.display = 'none'; // Скрываем кнопку скачивания
                passwordField.value = ''; // Очищаем значение, чтобы Java увидела пустую строку
                this.textContent = 'Изменить пароль';
            }
        });
    }

    // Изначально скрываем контейнер пароля и кнопку скачивания
    if (passwordContainer) passwordContainer.style.display = 'none';
    if (downloadBtn) downloadBtn.style.display = 'none';

    // 2. Логика подтверждения перед сохранением (без скачивания)
    if (saveBtn) {
        saveBtn.addEventListener('click', function(e) {
            // Если парольное поле скрыто или пусто, просто сохраняем логин/ФИО
            // Если поле открыто и там что-то есть, предупреждаем о смене пароля
            if (passwordContainer.style.display !== 'none' && passwordField.value.trim() !== "") {
                if (!confirm("Вы собираетесь изменить пароль пользователя. Продолжить?")) {
                    e.preventDefault();
                }
            } else {
                if (!confirm("Сохранить изменения в логине и ФИО?")) {
                    e.preventDefault();
                }
            }
            // Флаг скачивания оставляем false
            downloadFlag.value = "false";
        });
    }

    // 3. Логика подтверждения перед скачиванием файла
    if (downloadBtn) {
        downloadBtn.addEventListener('click', function(e) {
            if (passwordField.value.trim() === "") {
                alert("Поле пароля пусто. Невозможно создать файл с данными.");
                e.preventDefault();
                return;
            }

            if (confirm("ВНИМАНИЕ: Будет создан файл с новым паролем. Вы уверены, что хотите сохранить изменения и скачать файл?")) {
                downloadFlag.value = "true";
            } else {
                e.preventDefault();
            }
        });
    }
});

// Функция копирования (остается глобальной, так как вызывается из HTML onclick)
function copyToClipboard(elementId) {
    const copyText = document.getElementById(elementId);
    copyText.select();
    copyText.setSelectionRange(0, 99999);

    navigator.clipboard.writeText(copyText.value).then(() => {
        alert("Скопировано: " + copyText.value);
    }).catch(err => {
        console.error('Ошибка копирования: ', err);
    });
}