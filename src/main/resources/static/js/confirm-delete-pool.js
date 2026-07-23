document.addEventListener('DOMContentLoaded', function() {
    const deleteButtons = document.querySelectorAll('.delete-pool-btn');

    deleteButtons.forEach(btn => {
        btn.addEventListener('click', function() {
            const poolId = this.getAttribute('data-id');
            const poolName = this.getAttribute('data-name');
            const password = prompt(`Вы действительно хотите удалить бассейн "${poolName}"?\n\nДля подтверждения введите ВАШ текущий пароль:`);


            if (!password || password.trim() === '') {
                return;
            }


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