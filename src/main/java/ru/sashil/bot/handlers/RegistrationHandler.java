package ru.sashil.bot.handlers;

import ru.sashil.common.util.CommandUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class RegistrationHandler {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    private final Map<Long, Map<String, String>> tempData = new ConcurrentHashMap<>();
    private final Map<Long, Integer> steps = new ConcurrentHashMap<>();

    public boolean isRegistering(long userId) {
        return steps.containsKey(userId) && steps.get(userId) > 0;
    }

    public String processStep(long userId, String text) {
        int step = steps.getOrDefault(userId, 0);
        Map<String, String> data = tempData.computeIfAbsent(userId, k -> new HashMap<>());

        
        String cmd = CommandUtils.normalize(text);

        if (cmd.equals("начать") || cmd.equals("старт") || cmd.equals("справка")) {
            return "Сейчас идет регистрация. Пожалуйста, введите требуемые данные. Для отмены напишите 'Отмена'.";
        }

        if (cmd.equals("отмена")) {
            clearData(userId);
            return "Регистрация отменена.";
        }

        switch (step) {
            case 1: 
                data.put("lastName", text);
                steps.put(userId, 2);
                return "Введите ваше имя:";
            case 2: 
                data.put("firstName", text);
                steps.put(userId, 3);
                return "Введите отчество (или напишите 'нет', если нет):";
            case 3: 
                String middleName = cmd.equals("нет") ? null : text;
                data.put("middleName", middleName);
                steps.put(userId, 4);
                return "Введите email (или напишите 'пропустить'):";
            case 4: 
                if (!cmd.equals("пропустить")) {
                    if (!EMAIL_PATTERN.matcher(text).matches()) {
                        return "Неверный формат email. Попробуйте снова или напишите 'пропустить'.";
                    }
                    data.put("email", text);
                }
                steps.remove(userId);
                return "SAVE_PARENT";
            default:
                return "";
        }
    }

    public void startRegistration(long userId) {
        steps.put(userId, 1);
        tempData.computeIfAbsent(userId, k -> new HashMap<>());
    }

    public Map<String, String> getData(long userId) {
        return tempData.get(userId);
    }

    public void clearData(long userId) {
        tempData.remove(userId);
        steps.remove(userId);
    }
}