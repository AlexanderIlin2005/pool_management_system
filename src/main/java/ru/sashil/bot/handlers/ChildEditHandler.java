package ru.sashil.bot.handlers;

import ru.sashil.common.util.CommandUtils;
import ru.sashil.common.util.DateUtils;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChildEditHandler {
    private final Map<Long, Integer> steps = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> tempData = new ConcurrentHashMap<>();
    private final Map<Long, Long> editingChildId = new ConcurrentHashMap<>(); // Храним ID редактируемого ребенка

    public boolean isEditingChild(long userId) {
        return steps.containsKey(userId);
    }

    public void startEditingChild(long userId, long childId, Map<String, Object> currentData) {
        steps.put(userId, 1);
        editingChildId.put(userId, childId);

        Map<String, String> data = new ConcurrentHashMap<>();
        // Преобразуем данные из БД в строки для удобства
        for (Map.Entry<String, Object> entry : currentData.entrySet()) {
            data.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        tempData.put(userId, data);
    }

    public String processStep(long userId, String text, DatabaseService dbService) throws Exception {
        int step = steps.getOrDefault(userId, 0);
        Map<String, String> data = tempData.get(userId);
        if (data == null) return "Ошибка сессии.";

        String cmd = CommandUtils.normalize(text);
        if (cmd.equals("отмена")) {
            cancel(userId);
            return "Редактирование отменено.";
        }

        switch (step) {
            case 1: // Фамилия
                if (!cmd.equals("пропустить")) data.put("lastName", text);
                steps.put(userId, 2);
                return "Введите новое имя (или 'пропустить'):";

            case 2: // Имя
                if (!cmd.equals("пропустить")) data.put("firstName", text);
                steps.put(userId, 3);
                return "Введите новое отчество (или 'пропустить'):";

            case 3: // Отчество
                if (!cmd.equals("пропустить")) {
                    data.put("middleName", cmd.equals("нет") ? null : text);
                }
                steps.put(userId, 4);
                return "Введите новую дату рождения в формате ДД.ММ.ГГГГ (или 'пропустить'):";

            case 4: // Дата рождения
                if (!cmd.equals("пропустить")) {
                    String sqlDate = DateUtils.normalizeDate(text);
                    if (sqlDate == null) return "Неверный формат даты. Используйте ДД.ММ.ГГГГ.";
                    if (LocalDate.parse(sqlDate).isAfter(LocalDate.now())) return "Дата не может быть в будущем.";
                    data.put("birthDate", sqlDate);
                }
                steps.put(userId, 5);
                return "Введите новый номер класса (1-11) (или 'пропустить'):";

            case 5: // Класс (номер)
                if (!cmd.equals("пропустить")) {
                    try {
                        int grade = Integer.parseInt(text);
                        if (grade < 1 || grade > 11) return "Класс должен быть от 1 до 11.";

                        // Проверка возраста
                        LocalDate birthDate = LocalDate.parse(data.get("birthDate"));
                        int age = Period.between(birthDate, LocalDate.now()).getYears();
                        int minAge = grade + 5;
                        int maxAge = grade + 7;

                        if (age < minAge || age > maxAge) {
                            return "Возраст (" + age + ") не соответствует классу " + grade + ". Ожидаемо: " + minAge + "-" + maxAge;
                        }
                        data.put("gradeNumber", String.valueOf(grade));
                    } catch (NumberFormatException e) {
                        return "Введите число от 1 до 11.";
                    }
                }
                steps.put(userId, 6);
                return "Введите полное название класса (или 'пропустить'):";

            case 6: // Название класса
                if (!cmd.equals("пропустить")) data.put("gradeName", text);
                steps.put(userId, 7);
                return "Выберите навык плавания:\n1. Не умеет\n2. Держится на воде\n3. Уверенно плавает\n(Введите номер или 'пропустить')";

            case 7: // Навык
                String skill = data.get("skill"); // По умолчанию оставляем старый
                if (!cmd.equals("пропустить")) {
                    if (text.equals("1")) skill = "не умеет";
                    else if (text.equals("2")) skill = "держится на воде";
                    else if (text.equals("3")) skill = "уверенно плавает";
                    else return "Введите номер от 1 до 3.";
                }
                data.put("skill", skill);

                // Сохраняем
                long childId = editingChildId.get(userId);
                dbService.updateChild(childId,
                        data.get("firstName"), data.get("lastName"), data.get("middleName"),
                        data.get("birthDate"), Integer.parseInt(data.get("gradeNumber")),
                        data.get("gradeName"), skill
                );

                cancel(userId);
                return "Данные ребенка успешно обновлены!";

            default:
                return "";
        }
    }

    public void cancel(long userId) {
        steps.remove(userId);
        tempData.remove(userId);
        editingChildId.remove(userId);
    }
}