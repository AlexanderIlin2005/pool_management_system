package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.repository.AdminUserRepository;
import ru.sashil.admin.repository.GroupRepository;
import ru.sashil.admin.repository.PoolRepository;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class GroupService {
    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PoolRepository poolRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private LessonService lessonService; 

    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    public List<Pool> getAllPools() {
        return poolRepository.findAll();
    }

    public void saveGroup(Group group) {
        boolean isNew = group.getId() == null;

        
        if (isNew) {
            if (groupRepository.existsByNumber(group.getNumber())) {
                throw new IllegalArgumentException("Группа с таким номером уже существует!");
            }
        } else {
            Optional<Group> existing = groupRepository.findById(group.getId());
            if (existing.isPresent() && !existing.get().getNumber().equals(group.getNumber())) {
                if (groupRepository.existsByNumber(group.getNumber())) {
                    throw new IllegalArgumentException("Группа с таким номером уже существует!");
                }
            }
        }

        
        validateDayTime(group.getDay1Start(), group.getDay1End(), "Понедельник");
        validateDayTime(group.getDay2Start(), group.getDay2End(), "Вторник");
        validateDayTime(group.getDay3Start(), group.getDay3End(), "Среда");
        validateDayTime(group.getDay4Start(), group.getDay4End(), "Четверг");
        validateDayTime(group.getDay5Start(), group.getDay5End(), "Пятница");
        validateDayTime(group.getDay6Start(), group.getDay6End(), "Суббота");
        validateDayTime(group.getDay7Start(), group.getDay7End(), "Воскресенье");

        groupRepository.save(group);

        
        lessonService.generateLessonsForGroup(group);
    }

    private void validateDayTime(LocalTime start, LocalTime end, String dayName) {
        if (start != null || end != null) {
            if (start == null || end == null) {
                throw new IllegalArgumentException("Для " + dayName + " укажите и время начала, и время окончания.");
            }
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("В " + dayName + " время окончания должно быть позже времени начала.");
            }
            long minutes = Duration.between(start, end).toMinutes();
            if (minutes < 60) {
                throw new IllegalArgumentException("В " + dayName + " длительность занятия должна быть не менее 60 минут (сейчас " + minutes + " мин).");
            }
        }
    }

    public Optional<Group> getGroupById(Long id) {
        return groupRepository.findById(id);
    }

    public void deleteGroup(Long id) {
        
        groupRepository.deleteById(id);
    }

    public List<AdminUser> getAllCoaches() {
        return adminUserRepository.findByRole(AdminUser.Role.COACH);
    }
}