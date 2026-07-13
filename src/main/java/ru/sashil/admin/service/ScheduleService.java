package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.GroupRepository;
import ru.sashil.admin.repository.PoolLessonRepository;
import ru.sashil.admin.repository.PoolRepository;
import ru.sashil.common.util.NameUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    @Autowired private GroupRepository groupRepository;
    @Autowired private PoolRepository poolRepository;
    @Autowired private PoolLessonRepository poolLessonRepository;

    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(23, 0);
    private static final long TOTAL_MINUTES = java.time.Duration.between(DAY_START, DAY_END).toMinutes();

    public List<Pool> getAvailablePools(AdminUser user) {
        if (user.getRole() == AdminUser.Role.ADMIN) {
            return poolRepository.findAll();
        } else if (user.getRole() == AdminUser.Role.COACH) {
            List<Group> groups = groupRepository.findByTrainer_Id(user.getId());
            Set<Long> poolIds = groups.stream()
                    .map(g -> g.getPool() != null ? g.getPool().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return poolRepository.findAllById(poolIds);
        }
        return Collections.emptyList();
    }

    /**
     * Загружает расписание ТОЛЬКО для конкретной недели и выбранных групп.
     * Данные берутся СТРОГО из БД. Никакого копипаста.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWeeklySchedule(AdminUser user, Long requestedPoolId, LocalDate weekStart) {
        List<Pool> availablePools;
        Long selectedPoolId;
        boolean showPoolSelector = false;
        List<Group> groups;

        // 1. Определяем доступные пулы и выбираем текущий
        if (user.getRole() == AdminUser.Role.ADMIN) {
            availablePools = poolRepository.findAll();
            showPoolSelector = availablePools.size() > 1;

            if (requestedPoolId != null && availablePools.stream().anyMatch(p -> p.getId().equals(requestedPoolId))) {
                selectedPoolId = requestedPoolId;
            } else {
                selectedPoolId = availablePools.isEmpty() ? null : availablePools.get(0).getId();
            }

            // Получаем группы ИМЕННО выбранного бассейна
            groups = selectedPoolId != null ? groupRepository.findByPool_Id(selectedPoolId) : Collections.emptyList();
        } else {
            // Тренер видит все свои группы, селектор скрыт
            availablePools = Collections.emptyList();
            selectedPoolId = null;
            groups = groupRepository.findByTrainer_Id(user.getId());
        }

        // 2. Вычисляем диапазон дат для запроса к БД
        LocalDate weekEnd = weekStart.plusDays(6);

        // 3. Запрашиваем занятия ИЗ БД строго за эту неделю для этих групп
        List<Long> groupIds = groups.stream().map(Group::getId).collect(Collectors.toList());
        List<PoolLesson> lessons = new ArrayList<>();
        if (!groupIds.isEmpty()) {
            lessons = poolLessonRepository.findByGroupIdInAndLessonDateBetweenOrderByStartTime(groupIds, weekStart, weekEnd);
        }

        // 4. Создаем карту: "ДеньНедели_ВремяНачала" -> ID занятия
        Map<String, Long> lessonMap = new HashMap<>();
        for (PoolLesson l : lessons) {
            int dayIdx = l.getLessonDate().getDayOfWeek().getValue();
            String key = dayIdx + "_" + l.getStartTime();
            lessonMap.put(key, l.getId());
        }

        // 5. Формируем слоты расписания на основе данных из БД
        Map<Integer, List<ScheduleSlot>> weekSchedule = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) weekSchedule.put(i, new ArrayList<>());

        for (Group g : groups) {
            addSlotForDay(weekSchedule, 1, g, user, lessonMap);
            addSlotForDay(weekSchedule, 2, g, user, lessonMap);
            addSlotForDay(weekSchedule, 3, g, user, lessonMap);
            addSlotForDay(weekSchedule, 4, g, user, lessonMap);
            addSlotForDay(weekSchedule, 5, g, user, lessonMap);
            addSlotForDay(weekSchedule, 6, g, user, lessonMap);
            addSlotForDay(weekSchedule, 7, g, user, lessonMap);
        }

        if (user.getRole() == AdminUser.Role.ADMIN) {
            calculateColumnSplits(weekSchedule);
        }

        // Линия текущего времени только для текущей недели
        LocalDate today = LocalDate.now();
        LocalDateTime nowMoscow = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        double currentTimePercent = 0;
        if (nowMoscow.toLocalDate().equals(today)) {
            if (!nowMoscow.toLocalTime().isBefore(DAY_START) && !nowMoscow.toLocalTime().isAfter(DAY_END)) {
                long minutesFromStart = java.time.Duration.between(DAY_START, nowMoscow.toLocalTime()).toMinutes();
                currentTimePercent = (minutesFromStart * 100.0) / TOTAL_MINUTES;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("schedule", weekSchedule);
        result.put("currentDayIndex", today.getDayOfWeek().getValue());
        result.put("currentTimePercent", currentTimePercent);
        result.put("availablePools", availablePools);
        result.put("selectedPoolId", selectedPoolId);
        result.put("showPoolSelector", showPoolSelector);
        return result;
    }

    private void addSlotForDay(Map<Integer, List<ScheduleSlot>> map, int dayIndex, Group g, AdminUser user, Map<String, Long> lessonMap) {
        LocalTime start = getStartTime(g, dayIndex);
        LocalTime end = getEndTime(g, dayIndex);

        if (start != null && end != null && end.isAfter(start)) {
            ScheduleSlot slot = new ScheduleSlot();
            slot.setGroupId(g.getId());

            // Привязываем к реальному занятию из БД, если оно есть на этой неделе
            String key = dayIndex + "_" + start;
            slot.setLessonId(lessonMap.get(key)); // Может быть null, если занятие еще не сгенерировано

            slot.setGroupName(g.getName());
            slot.setGroupNumber(g.getNumber());
            slot.setStartTime(start);
            slot.setEndTime(end);
            String poolName = g.getPool() != null ? g.getPool().getName() : "Н/Д";
            slot.setPoolName(poolName);
            if (g.getTrainer() != null) {
                slot.setTrainerName(NameUtils.toInitials(g.getTrainer().getFullName()));
            }
            slot.setLeftPercent(0);
            slot.setWidthPercent(100);
            slot.setOverlapping(false);

            long startMin = java.time.Duration.between(DAY_START, start).toMinutes();
            long durationMin = java.time.Duration.between(start, end).toMinutes();
            slot.setTopPercent((startMin * 100.0) / TOTAL_MINUTES);
            slot.setHeightPercent((durationMin * 100.0) / TOTAL_MINUTES);
            map.get(dayIndex).add(slot);
        }
    }

    private void calculateColumnSplits(Map<Integer, List<ScheduleSlot>> schedule) {
        for (List<ScheduleSlot> slots : schedule.values()) {
            if (slots.isEmpty()) continue;
            slots.sort(Comparator.comparing(ScheduleSlot::getStartTime).thenComparing(ScheduleSlot::getEndTime));
            List<List<ScheduleSlot>> overlapGroups = new ArrayList<>();
            boolean[] assigned = new boolean[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                if (assigned[i]) continue;
                List<ScheduleSlot> currentGroup = new ArrayList<>();
                currentGroup.add(slots.get(i));
                assigned[i] = true;
                boolean changed = true;
                while (changed) {
                    changed = false;
                    for (int j = i + 1; j < slots.size(); j++) {
                        if (assigned[j]) continue;
                        ScheduleSlot candidate = slots.get(j);
                        for (ScheduleSlot groupSlot : currentGroup) {
                            if (candidate.getStartTime().isBefore(groupSlot.getEndTime()) &&
                                    groupSlot.getStartTime().isBefore(candidate.getEndTime())) {
                                currentGroup.add(candidate);
                                assigned[j] = true;
                                changed = true;
                                break;
                            }
                        }
                    }
                }
                overlapGroups.add(currentGroup);
            }
            for (List<ScheduleSlot> group : overlapGroups) {
                if (group.size() == 1) {
                    group.get(0).setOverlapping(false);
                    group.get(0).setLeftPercent(0);
                    group.get(0).setWidthPercent(100);
                } else {
                    double colWidth = 100.0 / group.size();
                    for (int k = 0; k < group.size(); k++) {
                        ScheduleSlot s = group.get(k);
                        s.setOverlapping(true);
                        s.setLeftPercent(k * colWidth);
                        s.setWidthPercent(colWidth);
                    }
                }
            }
        }
    }

    private LocalTime getStartTime(Group g, int day) {
        switch (day) {
            case 1: return g.getDay1Start(); case 2: return g.getDay2Start();
            case 3: return g.getDay3Start(); case 4: return g.getDay4Start();
            case 5: return g.getDay5Start(); case 6: return g.getDay6Start();
            case 7: return g.getDay7Start(); default: return null;
        }
    }

    private LocalTime getEndTime(Group g, int day) {
        switch (day) {
            case 1: return g.getDay1End(); case 2: return g.getDay2End();
            case 3: return g.getDay3End(); case 4: return g.getDay4End();
            case 5: return g.getDay5End(); case 6: return g.getDay6End();
            case 7: return g.getDay7End(); default: return null;
        }
    }
}