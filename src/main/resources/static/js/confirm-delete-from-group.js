document.addEventListener('DOMContentLoaded', function() {
    // Ищем именно тот класс, который указан в HTML форме исключения
    const forms = document.querySelectorAll('.remove-member-form');

    forms.forEach(form => {
        form.addEventListener('submit', function(e) {
            // Берем имя ребенка из скрытого поля childName
            const childNameInput = this.querySelector('input[name="childName"]');
            const childName = childNameInput ? childNameInput.value : 'этого участника';

            if (!confirm('Вы точно хотите исключить ' + childName + ' из группы?')) {
                e.preventDefault(); // Отменяем отправку формы
            }
        });
    });
});