package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.model.ScheduleSlot;
import ru.sashil.admin.repository.GroupRepository;
import ru.sashil.admin.repository.PoolRepository;
import ru.sashil.common.util.NameUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PoolRepository poolRepository;

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

    @Transactional(readOnly = true)
    public Map<String, Object> getWeeklySchedule(AdminUser user, Long poolId) {
        List<Group> groups;

        if (user.getRole() == AdminUser.Role.ADMIN) {
            groups = groupRepository.findByPool_Id(poolId);
        } else if (user.getRole() == AdminUser.Role.COACH) {
            groups = groupRepository.findByTrainer_IdAndPool_Id(user.getId(), poolId);
        } else {
            groups = Collections.emptyList();
        }

        Map<Integer, List<ScheduleSlot>> weekSchedule = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) weekSchedule.put(i, new ArrayList<>());

        for (Group g : groups) {
            addSlotForDay(weekSchedule, 1, g, user);
            addSlotForDay(weekSchedule, 2, g, user);
            addSlotForDay(weekSchedule, 3, g, user);
            addSlotForDay(weekSchedule, 4, g, user);
            addSlotForDay(weekSchedule, 5, g, user);
            addSlotForDay(weekSchedule, 6, g, user);
            addSlotForDay(weekSchedule, 7, g, user);
        }

        // Для админа рассчитываем разделение колонок
        if (user.getRole() == AdminUser.Role.ADMIN) {
            calculateColumnSplits(weekSchedule);
        }

        LocalDateTime nowMoscow = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        LocalDate today = nowMoscow.toLocalDate();
        int dayOfWeek = today.getDayOfWeek().getValue();

        double currentTimePercent = 0;
        if (!nowMoscow.toLocalTime().isBefore(DAY_START) && !nowMoscow.toLocalTime().isAfter(DAY_END)) {
            long minutesFromStart = java.time.Duration.between(DAY_START, nowMoscow.toLocalTime()).toMinutes();
            currentTimePercent = (minutesFromStart * 100.0) / TOTAL_MINUTES;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("schedule", weekSchedule);
        result.put("currentDayIndex", dayOfWeek);
        result.put("currentTimePercent", currentTimePercent);
        result.put("dayStart", DAY_START);
        result.put("dayEnd", DAY_END);

        return result;
    }

    private void addSlotForDay(Map<Integer, List<ScheduleSlot>> map, int dayIndex, Group g, AdminUser user) {
        LocalTime start = getStartTime(g, dayIndex);
        LocalTime end = getEndTime(g, dayIndex);

        if (start != null && end != null && end.isAfter(start)) {
            ScheduleSlot slot = new ScheduleSlot();
            slot.setGroupId(g.getId());
            slot.setGroupName(g.getName());
            slot.setGroupNumber(g.getNumber());
            slot.setStartTime(start);
            slot.setEndTime(end);

            String poolName = g.getPool() != null ? g.getPool().getName() : "Н/Д";
            slot.setPoolName(poolName);

            // Форматирование имени тренера через утилиту
            if (g.getTrainer() != null) {
                slot.setTrainerName(NameUtils.toInitials(g.getTrainer().getFullName()));
            }

            // По умолчанию полная ширина
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

    /**
     * Алгоритм разделения пересекающихся занятий на под-колонки.
     * Работает только для ADMIN视图.
     */
    private void calculateColumnSplits(Map<Integer, List<ScheduleSlot>> schedule) {
        for (List<ScheduleSlot> slots : schedule.values()) {
            if (slots.isEmpty()) continue;

            // Сортируем по времени начала, затем по длительности
            slots.sort(Comparator.comparing(ScheduleSlot::getStartTime)
                    .thenComparing(ScheduleSlot::getEndTime));

            // Находим группы пересекающихся событий
            List<List<ScheduleSlot>> overlapGroups = new ArrayList<>();
            boolean[] assigned = new boolean[slots.size()];

            for (int i = 0; i < slots.size(); i++) {
                if (assigned[i]) continue;

                List<ScheduleSlot> currentGroup = new ArrayList<>();
                currentGroup.add(slots.get(i));
                assigned[i] = true;

                // Ищем все события, которые пересекаются с текущей группой
                boolean changed = true;
                while (changed) {
                    changed = false;
                    for (int j = i + 1; j < slots.size(); j++) {
                        if (assigned[j]) continue;

                        ScheduleSlot candidate = slots.get(j);
                        // Проверяем пересечение кандидата с ЛЮБЫМ элементом в группе
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

            // Распределяем ширину для каждой группы пересечений
            for (List<ScheduleSlot> group : overlapGroups) {
                if (group.size() == 1) {
                    // Нет пересечений, полная ширина
                    group.get(0).setOverlapping(false);
                    group.get(0).setLeftPercent(0);
                    group.get(0).setWidthPercent(100);
                } else {
                    // Есть пересечения, делим ширину поровну
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