package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.AttendanceRepository;
import ru.sashil.admin.repository.ChildRepository;
import ru.sashil.admin.repository.PoolLessonRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepo;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private PoolLessonRepository lessonRepo;

    /**
     * Получает список детей группы.
     * Используем нативный запрос, чтобы избежать ошибок маппинга Enum и LazyLoading.
     */
    public List<Child> getEligibleChildren(Long groupId, LocalDate lessonDate) {
        // Возвращаем всех детей, которые сейчас числятся в группе
        return childRepository.findByGroupIdNative(groupId);
    }

    @Transactional
    public void saveAttendance(Long lessonId, Map<Long, String> marks, AdminUser marker) {
        if (marks == null || marks.isEmpty()) return;

        for (Map.Entry<Long, String> entry : marks.entrySet()) {
            Long childId = entry.getKey();
            String statusStr = entry.getValue();

            if (statusStr == null || statusStr.isEmpty()) continue;

            try {
                Attendance.Status status = Attendance.Status.fromLabel(statusStr);

                Optional<Attendance> existing = attendanceRepo.findByLessonIdAndChildId(lessonId, childId);

                Attendance attendance;
                if (existing.isPresent()) {
                    attendance = existing.get();
                } else {
                    attendance = new Attendance();
                    PoolLesson lesson = new PoolLesson();
                    lesson.setId(lessonId);
                    attendance.setLesson(lesson);

                    Child child = new Child();
                    child.setId(childId);
                    attendance.setChild(child);
                }

                attendance.setStatus(status);
                attendance.setMarkedBy(marker);
                attendance.setMarkedAt(LocalDateTime.now());

                attendanceRepo.save(attendance);
            } catch (IllegalArgumentException e) {
                System.err.println("Неверный статус: " + statusStr);
            }
        }
    }

    public boolean canMarkAttendance(PoolLesson lesson, AdminUser user) {
        if (user == null) return false;
        if (user.getRole() == AdminUser.Role.ADMIN) return true;

        if (user.getRole() == AdminUser.Role.COACH && lesson.getGroup().getTrainer() != null) {
            return lesson.getGroup().getTrainer().getId().equals(user.getId());
        }
        return false;
    }

    public List<Attendance> getByLessonId(Long lessonId) {
        return attendanceRepo.findByLessonId(lessonId);
    }
}