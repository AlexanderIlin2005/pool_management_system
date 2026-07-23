document.addEventListener('DOMContentLoaded', function() {
    const forms = document.querySelectorAll('.delete-group-form');

    forms.forEach(form => {
        form.addEventListener('submit', function(e) {
            const groupNameInput = this.querySelector('input[name="groupName"]');
            const groupName = groupNameInput ? groupNameInput.value : 'эту группу';

            if (!confirm('Вы точно хотите удалить группу \'' + groupName + '\'? Это действие нельзя отменить.')) {
                e.preventDefault();
            }
        });
    });
});