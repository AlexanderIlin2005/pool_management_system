package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.Holiday;
import ru.sashil.admin.model.SchoolVacation;
import ru.sashil.admin.repository.GroupRepository;
import ru.sashil.admin.repository.HolidayRepository;
import ru.sashil.admin.repository.SchoolVacationRepository;

import java.time.LocalDate;
import java.util.List;

@Service
public class CalendarService {

    @Autowired
    private HolidayRepository holidayRepo;

    @Autowired
    private SchoolVacationRepository vacationRepo;

    @Autowired
    private GroupRepository groupRepo;

    @Autowired
    private LessonService lessonService;

    public List<Holiday> getAllHolidays() {
        return holidayRepo.findAllByOrderByHolidayDateAsc();
    }

    public List<SchoolVacation> getAllVacations() {
        return vacationRepo.findAllByOrderByStartDateAsc();
    }

    @Transactional
    public void addHoliday(LocalDate date, String name) {
        if (!holidayRepo.existsByHolidayDate(date)) {
            Holiday holiday = new Holiday();
            holiday.setHolidayDate(date);
            holiday.setName(name);
            holidayRepo.save(holiday);

            
            regenerateAllSchedules();
        }
    }

    @Transactional
    public void deleteHoliday(Long id) {
        holidayRepo.deleteById(id);

        
        regenerateAllSchedules();
    }

    @Transactional
    public void addVacation(LocalDate start, LocalDate end, String name) {
        if (end.isBefore(start)) return;

        SchoolVacation vacation = new SchoolVacation();
        vacation.setStartDate(start);
        vacation.setEndDate(end);
        vacation.setName(name);
        vacationRepo.save(vacation);

        
        regenerateAllSchedules();
    }

    @Transactional
    public void deleteVacation(Long id) {
        vacationRepo.deleteById(id);

        
        regenerateAllSchedules();
    }

    /**
     * Перегенерирует будущие занятия для всех активных групп.
     */
    private void regenerateAllSchedules() {
        List<Group> allGroups = groupRepo.findAll();
        for (Group group : allGroups) {
            try {
                lessonService.regenerateFutureLessons(group);
            } catch (Exception e) {
                
                System.err.println("Ошибка при перегенерации расписания для группы " + group.getId() + ": " + e.getMessage());
            }
        }
    }
}