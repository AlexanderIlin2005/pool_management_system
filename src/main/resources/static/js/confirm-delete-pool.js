document.addEventListener('DOMContentLoaded', function() {
    const deleteButtons = document.querySelectorAll('.delete-pool-btn');

    deleteButtons.forEach(btn => {
        btn.addEventListener('click', function() {
            const poolId = this.getAttribute('data-id');
            const poolName = this.getAttribute('data-name');

            // Запрашиваем пароль через системное окно
            const password = prompt(`Вы действительно хотите удалить бассейн "${poolName}"?\n\nДля подтверждения введите ВАШ текущий пароль:`);

            // Если пользователь нажал Отмена (null) или оставил поле пустым
            if (!password || password.trim() === '') {
                return;
            }

            // Создаем скрытую форму динамически и отправляем её
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/pools/delete/' + poolId;

            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'adminPassword';
            input.value = password;

            form.appendChild(input);
            document.body.appendChild(form);
            form.submit();
        });
    });
});