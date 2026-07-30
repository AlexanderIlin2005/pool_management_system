document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM загружен, ищу кнопки .toggle-certificate-btn...');

    const buttons = document.querySelectorAll('.toggle-certificate-btn');

    console.log('Найдено кнопок:', buttons.length);

    buttons.forEach(function(btn) {
        console.log('Кнопка:', btn);
        console.log('data-child-id:', btn.dataset.childId);
        console.log('data-child-name:', btn.dataset.childName);
        console.log('data-current-status:', btn.dataset.currentStatus);

        btn.addEventListener('click', function(e) {
            console.log('Клик по кнопке!');
            e.preventDefault();

            const childId = this.dataset.childId;
            const childName = this.dataset.childName;
            const currentStatus = this.dataset.currentStatus === 'true';

            console.log('childId:', childId);
            console.log('childName:', childName);
            console.log('currentStatus:', currentStatus);

            const confirmMessage = currentStatus
                ? 'Вы уверены, что хотите снять отметку о получении оригинала справки для ребенка ' + childName + '?'
                : 'Вы уверены, что хотите отметить получение оригинала справки для ребенка ' + childName + '?';

            if (confirm(confirmMessage)) {
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = '/children/certificate/toggle/' + childId;
                document.body.appendChild(form);
                form.submit();
            }
        });
    });
});