package ru.sashil.bot.handlers;

import ru.sashil.common.service.DatabaseService;
import ru.sashil.common.util.CommandUtils;
import ru.sashil.common.util.PhoneUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ProfileEditHandler {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    private final Map<Long, Integer> editSteps = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> tempData = new ConcurrentHashMap<>();

    public boolean isEditing(long userId) {
        return editSteps.containsKey(userId);
    }

    public void startEditing(long userId, Map<String, String> currentData) {
        editSteps.put(userId, 1);
        Map<String, String> safeData = new ConcurrentHashMap<>();
        if (currentData != null) {
            for (Map.Entry<String, String> entry : currentData.entrySet()) {
                safeData.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        tempData.put(userId, safeData);
    }

    public String processStep(long userId, String text, DatabaseService dbService) throws Exception {
        int step = editSteps.getOrDefault(userId, 0);

        // Проверка на отмену на ЛЮБОМ этапе
        String cmd = CommandUtils.normalize(text);
        if (cmd.equals("отмена")) {
            cancelEditing(userId);
            return "Редактирование профиля отменено.";
        }

        if (step == 0) {
            return "Сессия редактирования не найдена. Напишите 'Редактировать' снова.";
        }

        Map<String, String> data = tempData.get(userId);
        if (data == null) {
            data = new ConcurrentHashMap<>();
            tempData.put(userId, data);
        }

        switch (step) {
            case 1: // Фамилия
                if (!cmd.equals("пропустить")) {
                    data.put("lastName", text);
                }
                editSteps.put(userId, 2);
                return "Введите новое имя (или 'пропустить'):";

            case 2: // Имя
                if (!cmd.equals("пропустить")) {
                    data.put("firstName", text);
                }
                editSteps.put(userId, 3);
                return "Введите новое отчество (или 'пропустить'):";

            case 3: // Отчество
                if (!cmd.equals("пропустить")) {
                    data.put("middleName", cmd.equals("нет") ? null : text);
                }
                editSteps.put(userId, 4);
                return "Введите новый email (или 'пропустить'):";

            case 4: // Email
                if (!cmd.equals("пропустить")) {
                    if (!EMAIL_PATTERN.matcher(text).matches()) return "Неверный формат email. Попробуйте снова.";
                    data.put("email", text);
                }
                editSteps.put(userId, 5);
                return "Введите новый телефон (формат +7..., или 'пропустить'):";

            case 5: // Телефон
                if (!cmd.equals("пропустить")) {
                    String normalized = PhoneUtils.normalize(text);
                    if (normalized == null) {
                        return "Неверный формат телефона. Используйте +7 или 8 (10 цифр). Пример: 89601234567";
                    }
                    data.put("phone", normalized);
                }

                dbService.updateParent(userId,
                        isEmpty(data.get("firstName")) ? null : data.get("firstName"),
                        isEmpty(data.get("lastName")) ? null : data.get("lastName"),
                        isEmpty(data.get("middleName")) ? null : data.get("middleName"),
                        isEmpty(data.get("email")) ? null : data.get("email"),
                        isEmpty(data.get("phone")) ? null : data.get("phone")
                );

                editSteps.remove(userId);
                tempData.remove(userId);
                return "Профиль успешно обновлен!";
            default:
                return "";
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public void cancelEditing(long userId) {
        editSteps.remove(userId);
        tempData.remove(userId);
    }
}