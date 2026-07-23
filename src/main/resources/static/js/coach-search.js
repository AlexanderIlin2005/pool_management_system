document.addEventListener('DOMContentLoaded', function() {
    const searchInput = document.getElementById('coach-search');
    if (!searchInput) return;

    const resultsDiv = document.getElementById('coach-results');
    const hiddenIdInput = document.getElementById('coach-id-input');


    const coaches = window.coachesList || [];


    const currentTrainerId = hiddenIdInput.value;
    if (currentTrainerId) {
        const currentCoach = coaches.find(c => c.id == currentTrainerId);
        if (currentCoach) {
            searchInput.value = currentCoach.fullName;
        }
    }


    searchInput.addEventListener('input', function() {
        const query = this.value.toLowerCase();
        resultsDiv.innerHTML = '';

        if (!query) {
            resultsDiv.style.display = 'none';
            return;
        }

        const filtered = coaches.filter(c => c.fullName.toLowerCase().includes(query));

        if (filtered.length > 0) {
            resultsDiv.style.display = 'block';
            filtered.forEach(coach => {
                const div = document.createElement('div');
                div.textContent = coach.fullName;
                div.style.padding = '8px';
                div.style.cursor = 'pointer';
                div.style.borderBottom = '1px solid #eee';

                div.onmouseover = () => div.style.backgroundColor = '#f0f0f0';
                div.onmouseout = () => div.style.backgroundColor = 'white';

                div.onclick = () => {
                    searchInput.value = coach.fullName;
                    hiddenIdInput.value = coach.id; // Сохраняем ID в скрытое поле
                    resultsDiv.style.display = 'none';
                };

                resultsDiv.appendChild(div);
            });
        } else {
            resultsDiv.style.display = 'none';
        }
    });


    document.addEventListener('click', function(e) {
        if (!searchInput.contains(e.target) && !resultsDiv.contains(e.target)) {
            resultsDiv.style.display = 'none';
        }
    });
});