package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static ru.sashil.admin.service.ScheduleService.TOTAL_MINUTES;

@Service
public class LessonService {

    @Autowired private PoolLessonRepository lessonRepo;
    @Autowired private GroupRepository groupRepo;
    @Autowired private HolidayRepository holidayRepo;
    @Autowired private SchoolVacationRepository vacationRepo;

    /**
     * Генерирует занятия для группы ОТ ДАТЫ ЕЁ СОЗДАНИЯ до 31 мая текущего/следующего года.
     */
    @Transactional
    public void generateLessonsForGroup(Group group) {
        final Long groupId = group.getId();

        
        LocalDate creationDate = group.getCreatedAt() != null ?
                group.getCreatedAt().toLocalDate() : LocalDate.now();
        LocalDate today = LocalDate.now();
        LocalDate startDate = creationDate.isAfter(today) ? creationDate : today;

        
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            startDate = startDate.with(DayOfWeek.MONDAY);
        }

        
        LocalDate endDate = LocalDate.of(today.getYear(), 5, 31);
        if (endDate.isBefore(today)) {
            endDate = endDate.plusYears(1);
        }

        Set<LocalDate> holidays = new HashSet<>(holidayRepo.findAll().stream()
                .map(Holiday::getHolidayDate).toList());

        List<SchoolVacation> vacations = vacationRepo.findAll();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            final LocalDate currentDate = date;

            if (holidays.contains(currentDate)) continue;

            boolean inVacation = vacations.stream()
                    .anyMatch(v -> !currentDate.isBefore(v.getStartDate()) &&
                            !currentDate.isAfter(v.getEndDate()));
            if (inVacation) continue;

            DayOfWeek dow = currentDate.getDayOfWeek();
            int dayIndex = dow.getValue();

            LocalTime start = getDayStart(group, dayIndex);
            LocalTime end = getDayEnd(group, dayIndex);

            if (start != null && end != null) {
                final LocalTime currentStart = start;

                if (!lessonRepo.existsByGroupIdAndLessonDateAndStartTime(groupId, currentDate, currentStart)) {
                    PoolLesson lesson = new PoolLesson();

                    
                    Group g = new Group();
                    g.setId(groupId);
                    lesson.setGroup(g);

                    lesson.setLessonDate(currentDate);
                    lesson.setStartTime(currentStart);
                    lesson.setEndTime(end);

                    lessonRepo.save(lesson);
                }
            }
        }
    }

    /**
     * Получает конкретное занятие по ID для страницы отметки посещаемости.
     * Проверяет права доступа.
     */
    public Optional<PoolLesson> getLessonForAttendance(Long lessonId, AdminUser user) {
        Optional<PoolLesson> lessonOpt = lessonRepo.findById(lessonId);
        if (lessonOpt.isEmpty()) return Optional.empty();

        PoolLesson lesson = lessonOpt.get();

        
        if (user.getRole() == AdminUser.Role.ADMIN) {
            return lessonOpt;
        }

        
        if (user.getRole() == AdminUser.Role.COACH && lesson.getGroup().getTrainer() != null) {
            if (lesson.getGroup().getTrainer().getId().equals(user.getId())) {
                return lessonOpt;
            }
        }

        return Optional.empty();
    }

    @Transactional
    public void regenerateFutureLessons(Group group) {
        LocalDate today = LocalDate.now();
        List<PoolLesson> futureLessons = lessonRepo.findByGroupIdAndLessonDateBetweenOrderByStartTime(
                group.getId(), today, LocalDate.of(2099, 12, 31));
        lessonRepo.deleteAll(futureLessons);

        generateLessonsForGroup(group);
    }

    private LocalTime getDayStart(Group g, int day) {
        switch (day) {
            case 1: return g.getDay1Start(); case 2: return g.getDay2Start();
            case 3: return g.getDay3Start(); case 4: return g.getDay4Start();
            case 5: return g.getDay5Start(); case 6: return g.getDay6Start();
            case 7: return g.getDay7Start(); default: return null;
        }
    }

    private LocalTime getDayEnd(Group g, int day) {
        switch (day) {
            case 1: return g.getDay1End(); case 2: return g.getDay2End();
            case 3: return g.getDay3End(); case 4: return g.getDay4End();
            case 5: return g.getDay5End(); case 6: return g.getDay6End();
            case 7: return g.getDay7End(); default: return null;
        }
    }

    public PoolLesson getLessonByGroupAndDate(Long groupId, LocalDate date) {
        return lessonRepo.findByGroupIdAndLessonDate(groupId, date).orElse(null);
    }


}