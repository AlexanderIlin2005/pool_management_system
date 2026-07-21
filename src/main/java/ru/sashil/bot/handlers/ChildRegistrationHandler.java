package ru.sashil.bot.handlers;

import ru.sashil.common.service.DatabaseService;
import ru.sashil.common.util.CommandUtils;
import ru.sashil.common.util.DateUtils;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChildRegistrationHandler {
    private final Map<Long, Integer> steps = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> tempData = new ConcurrentHashMap<>();

    public boolean isAddingChild(long userId) {
        return steps.containsKey(userId);
    }

    public void startAddingChild(long userId) {
        steps.put(userId, 1);
        tempData.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
    }

    public String processStep(long userId, String text, DatabaseService dbService) throws Exception {
        int step = steps.getOrDefault(userId, 0);
        Map<String, String> data = tempData.get(userId);
        if (data == null) return "Ошибка сессии. Начните заново.";

        String cmd = CommandUtils.normalize(text);

        if (cmd.equals("отмена")) {
            cancel(userId);
            return "Добавление ребенка отменено.";
        }

        switch (step) {
            case 1: 
                data.put("lastName", text);
                steps.put(userId, 2);
                return "Введите имя ребенка:";

            case 2: 
                data.put("firstName", text);
                steps.put(userId, 3);
                return "Введите отчество ребенка (или 'нет'):";

            case 3: 
                if (!cmd.equals("нет")) data.put("middleName", text);
                steps.put(userId, 4);
                return "Введите дату рождения в формате ДД.ММ.ГГГГ (например, 08.03.2018):";

            case 4: 
                String sqlDate = DateUtils.normalizeDate(text);
                if (sqlDate == null) return "Неверный формат даты. Используйте ДД.ММ.ГГГГ (например, 8.3.2018).";

                
                if (LocalDate.parse(sqlDate).isAfter(LocalDate.now())) return "Дата рождения не может быть в будущем.";

                data.put("birthDate", sqlDate);
                steps.put(userId, 5);
                return "Введите номер класса (от 1 до 11):";

            case 5: 
                try {
                    int grade = Integer.parseInt(text);
                    if (grade < 1 || grade > 11) return "Класс должен быть от 1 до 11.";

                    LocalDate birthDate = LocalDate.parse(data.get("birthDate"));
                    int age = Period.between(birthDate, LocalDate.now()).getYears();

                    
                    
                    int minAge = grade + 5;
                    int maxAge = grade + 7;

                    if (age < minAge || age > maxAge) {
                        return "Возраст ребенка (" + age + " лет) не соответствует " + grade + "-му классу. Ожидаемый возраст: " + minAge + "-" + maxAge + " лет.";
                    }

                    data.put("gradeNumber", String.valueOf(grade));
                    steps.put(userId, 6);
                    return "Введите полное название класса (например, 5Б или 5-корпус А):";

                } catch (NumberFormatException e) {
                    return "Введите число от 1 до 11.";
                }

            case 6: 
                data.put("gradeName", text);
                steps.put(userId, 7);
                return "Выберите навык плавания:\n1. Не умеет\n2. Держится на воде\n3. Уверенно плавает\n(Введите номер)";

            case 7: 
                String skill = "";
                if (text.equals("1")) skill = "не умеет";
                else if (text.equals("2")) skill = "держится на воде";
                else if (text.equals("3")) skill = "уверенно плавает";
                else return "Введите номер от 1 до 3.";

                data.put("skill", skill);

                
                dbService.addChild(userId,
                        data.get("firstName"),
                        data.get("lastName"),
                        data.get("middleName"),
                        data.get("birthDate"),
                        Integer.parseInt(data.get("gradeNumber")),
                        data.get("gradeName"),
                        skill
                );

                cancel(userId);
                return "Ребенок успешно добавлен!";

            default:
                return "";
        }
    }

    public void cancel(long userId) {
        steps.remove(userId);
        tempData.remove(userId);
    }
}