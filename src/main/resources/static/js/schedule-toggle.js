document.addEventListener('DOMContentLoaded', function() {
    const days = [1, 2, 3, 4, 5, 6, 7];

    days.forEach(day => {
        const startInput = document.querySelector(`input[name="day${day}Start"]`);
        const endInput = document.querySelector(`input[name="day${day}End"]`);
        const block = document.getElementById(`time-block-${day}`);
        const btn = document.querySelector(`.day-toggle[data-day="${day}"]`);

        if (!btn || !block) return;

        // Если при редактировании есть данные, показываем блок и активируем кнопку
        if ((startInput && startInput.value) || (endInput && endInput.value)) {
            block.style.display = 'block';
            btn.classList.add('active');
            btn.style.backgroundColor = '#007bff';
            btn.style.color = 'white';
            btn.style.borderColor = '#007bff';
        }

        // Обработчик клика
        btn.addEventListener('click', function() {
            const isActive = this.classList.contains('active');

            if (isActive) {
                // Скрываем и очищаем
                block.style.display = 'none';
                if (startInput) startInput.value = '';
                if (endInput) endInput.value = '';
                this.classList.remove('active');
                this.style.backgroundColor = '#f8f9fa';
                this.style.color = 'black';
                this.style.borderColor = '#ccc';
            } else {
                // Показываем
                block.style.display = 'block';
                this.classList.add('active');
                this.style.backgroundColor = '#007bff';
                this.style.color = 'white';
                this.style.borderColor = '#007bff';
            }
        });
    });
});