document.addEventListener('DOMContentLoaded', function() {
    const passwordContainer = document.getElementById('passwordContainer');
    const passwordField = document.getElementById('passwordField');
    const toggleBtn = document.getElementById('togglePasswordBtn');
    const downloadBtn = document.getElementById('downloadBtn');
    const saveBtn = document.getElementById('saveBtn');
    const downloadFlag = document.getElementById('downloadFlag');


    if (toggleBtn) {
        toggleBtn.addEventListener('click', function() {
            const isHidden = passwordContainer.style.display === 'none';

            if (isHidden) {

                if (confirm("Вы действительно хотите изменить пароль? Это действие потребует ввода нового пароля.")) {
                    passwordContainer.style.display = 'block';
                    downloadBtn.style.display = 'inline-block';
                    passwordField.focus();
                    this.textContent = 'Скрыть поле пароля';
                }
            } else {
                passwordContainer.style.display = 'none';
                downloadBtn.style.display = 'none';
                passwordField.value = '';
                this.textContent = 'Изменить пароль';
            }
        });
    }


    if (passwordContainer) passwordContainer.style.display = 'none';
    if (downloadBtn) downloadBtn.style.display = 'none';


    if (saveBtn) {
        saveBtn.addEventListener('click', function(e) {
            if (passwordContainer.style.display !== 'none' && passwordField.value.trim() !== "") {
                if (!confirm("Вы собираетесь изменить пароль пользователя. Продолжить?")) {
                    e.preventDefault();
                }
            } else {
                if (!confirm("Сохранить изменения в логине и ФИО?")) {
                    e.preventDefault();
                }
            }
            downloadFlag.value = "false";
        });
    }


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


function copyToClipboard(elementId) {
    const copyText = document.getElementById(elementId);
    copyText.select();
    copyText.setSelectionRange(0, 99999);

    navigator.clipboard.writeText(copyText.value).then(() => {
        alert("Пароль скопирован в буфер обмена");
    }).catch(err => {
        console.error('Ошибка копирования: ', err);
    });
}