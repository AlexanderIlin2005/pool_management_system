package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.*;
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
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private ChildRepository childRepository;

    
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private SchoolVacationRepository vacationRepository;

    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(23, 0);
    public static final long TOTAL_MINUTES = java.time.Duration.between(DAY_START, DAY_END).toMinutes();

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
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWeeklySchedule(AdminUser user, Long requestedPoolId, LocalDate weekStart) {
        List<Pool> availablePools;
        Long selectedPoolId;
        boolean showPoolSelector = false;
        List<Group> groups;

        
        if (user.getRole() == AdminUser.Role.ADMIN) {
            availablePools = poolRepository.findAll();
            showPoolSelector = availablePools.size() > 1;

            if (requestedPoolId != null && availablePools.stream().anyMatch(p -> p.getId().equals(requestedPoolId))) {
                selectedPoolId = requestedPoolId;
            } else {
                selectedPoolId = availablePools.isEmpty() ? null : availablePools.get(0).getId();
            }

            
            groups = selectedPoolId != null ? groupRepository.findByPool_Id(selectedPoolId) : Collections.emptyList();
        } else {
            
            availablePools = Collections.emptyList();
            selectedPoolId = null;
            groups = groupRepository.findByTrainer_Id(user.getId());
        }

        
        LocalDate weekEnd = weekStart.plusDays(6);

        
        List<Long> groupIds = groups.stream().map(Group::getId).collect(Collectors.toList());
        List<PoolLesson> lessons = new ArrayList<>();
        if (!groupIds.isEmpty()) {
            lessons = poolLessonRepository.findByGroupIdInAndLessonDateBetweenOrderByStartTime(groupIds, weekStart, weekEnd);
        }

        
        Map<String, Long> lessonMap = new HashMap<>();
        for (PoolLesson l : lessons) {
            int dayIdx = l.getLessonDate().getDayOfWeek().getValue();
            String key = dayIdx + "_" + l.getStartTime();
            lessonMap.put(key, l.getId());
        }

        
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

        
        LocalDate today = LocalDate.now();
        LocalDateTime nowMoscow = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
        double currentTimePercent = 0;
        if (nowMoscow.toLocalDate().equals(today)) {
            if (!nowMoscow.toLocalTime().isBefore(DAY_START) && !nowMoscow.toLocalTime().isAfter(DAY_END)) {
                long minutesFromStart = java.time.Duration.between(DAY_START, nowMoscow.toLocalTime()).toMinutes();
                currentTimePercent = (minutesFromStart * 100.0) / TOTAL_MINUTES;
            }
        }

        
        
        List<Holiday> holidays = holidayRepository.findAll();
        List<SchoolVacation> vacations = vacationRepository.findAll();

        
        Set<LocalDate> holidayDates = holidays.stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());

        Set<LocalDate> vacationDates = new HashSet<>();
        for (SchoolVacation v : vacations) {
            LocalDate d = v.getStartDate();
            while (!d.isAfter(v.getEndDate())) {
                vacationDates.add(d);
                d = d.plusDays(1);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("schedule", weekSchedule);
        result.put("currentDayIndex", today.getDayOfWeek().getValue());
        result.put("currentTimePercent", currentTimePercent);
        result.put("availablePools", availablePools);
        result.put("selectedPoolId", selectedPoolId);
        result.put("showPoolSelector", showPoolSelector);

        
        result.put("holidayDates", holidayDates);
        result.put("vacationDates", vacationDates);

        return result;
    }

    private void addSlotForDay(Map<Integer, List<ScheduleSlot>> map, int dayIndex, Group g, AdminUser user, Map<String, Long> lessonMap) {
        LocalTime start = getStartTime(g, dayIndex);
        LocalTime end = getEndTime(g, dayIndex);

        if (start != null && end != null && end.isAfter(start)) {
            ScheduleSlot slot = new ScheduleSlot();
            slot.setGroupId(g.getId());

            
            String key = dayIndex + "_" + start;
            slot.setLessonId(lessonMap.get(key)); 

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

    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyAttendance(Group group, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        
        List<ChildSimple> children = childRepository.findSimpleByGroupId(group.getId());

        
        List<PoolLesson> lessons = poolLessonRepository.findByGroupIdAndLessonDateBetweenOrderByStartTime(
                group.getId(), startDate, endDate);

        
        List<Long> lessonIds = lessons.stream().map(PoolLesson::getId).collect(Collectors.toList());
        List<Attendance> attendances = new ArrayList<>();
        if (!lessonIds.isEmpty()) {
            attendances = attendanceRepository.findByLessonIdIn(lessonIds);
        }

        
        Map<Long, Map<LocalDate, Attendance.Status>> attendanceMap = new HashMap<>();
        for (Attendance a : attendances) {
            Long childId = a.getChild().getId();
            LocalDate date = a.getLesson().getLessonDate();

            attendanceMap.computeIfAbsent(childId, k -> new HashMap<>())
                    .put(date, a.getStatus());
        }

        
        List<LocalDate> activeDays = new ArrayList<>();
        Set<LocalDate> lessonDates = lessons.stream().map(PoolLesson::getLessonDate).collect(Collectors.toSet());

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            if (lessonDates.contains(d)) {
                activeDays.add(d);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("days", activeDays);
        result.put("children", children);
        result.put("attendanceMap", attendanceMap);

        return result;
    }

}