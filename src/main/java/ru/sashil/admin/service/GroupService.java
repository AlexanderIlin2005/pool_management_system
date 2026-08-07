package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.AdminUser;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.model.SubscriptionType;
import ru.sashil.admin.repository.AdminUserRepository;
import ru.sashil.admin.repository.GroupRepository;
import ru.sashil.admin.repository.PoolRepository;
import ru.sashil.admin.repository.SubscriptionTypeRepository;

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

    @Autowired
    private SubscriptionTypeRepository subscriptionTypeRepository;

    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    public List<Pool> getAllPools() {
        return poolRepository.findAll();
    }

    public void saveGroup(Group group) {
        boolean isNew = group.getId() == null;

        // Проверка уникальности номера группы
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

        // Валидация расписания
        validateDayTime(group.getDay1Start(), group.getDay1End(), "Понедельник");
        validateDayTime(group.getDay2Start(), group.getDay2End(), "Вторник");
        validateDayTime(group.getDay3Start(), group.getDay3End(), "Среда");
        validateDayTime(group.getDay4Start(), group.getDay4End(), "Четверг");
        validateDayTime(group.getDay5Start(), group.getDay5End(), "Пятница");
        validateDayTime(group.getDay6Start(), group.getDay6End(), "Суббота");
        validateDayTime(group.getDay7Start(), group.getDay7End(), "Воскресенье");

        // Валидация критериев вступления
        validateEntryCriteria(group.getMinAge(), group.getMaxAge(), group.getSkill1(), group.getSkill2());

        // Валидация типа абонемента (проверяем, что ID существует в БД)
        if (group.getSubscriptionType() != null && group.getSubscriptionType().getId() != null) {
            boolean exists = subscriptionTypeRepository.existsById(group.getSubscriptionType().getId());
            if (!exists) {
                throw new IllegalArgumentException("Выбранный тип абонемента не существует!");
            }
        }

        groupRepository.save(group);

        // Генерируем занятия для группы
        lessonService.generateLessonsForGroup(group);
    }

    private void validateEntryCriteria(Integer minAge, Integer maxAge, String skill1, String skill2) {
        // Валидация возраста
        if (minAge != null) {
            if (minAge < 6 || minAge > 18) throw new IllegalArgumentException("Минимальный возраст должен быть от 6 до 18.");
        }
        if (maxAge != null) {
            if (maxAge < 6 || maxAge > 18) throw new IllegalArgumentException("Максимальный возраст должен быть от 6 до 18.");
        }
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new IllegalArgumentException("Минимальный возраст не может быть больше максимального.");
        }

        // Валидация навыков - только запрещенная комбинация
        if (skill1 != null && skill2 != null) {
            // Запрещенная комбинация крайностей
            boolean isExtremeCombo =
                    ("не умеет".equals(skill1) && "уверенно плавает".equals(skill2)) ||
                            ("уверенно плавает".equals(skill1) && "не умеет".equals(skill2));

            if (isExtremeCombo) {
                throw new IllegalArgumentException("Нельзя сочетать крайние навыки: 'не умеет' и 'уверенно плавает'.");
            }
        }
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