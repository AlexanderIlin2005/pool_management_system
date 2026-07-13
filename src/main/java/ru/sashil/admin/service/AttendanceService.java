package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.AttendanceRepository;
import ru.sashil.admin.repository.ChildRepository;
import ru.sashil.admin.repository.GroupChildRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttendanceService {
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private GroupChildRepository groupChildRepository;
    @Autowired private ChildRepository childRepository;

    public List<Attendance> getByLessonId(Long lessonId) {
        return attendanceRepository.findByLessonId(lessonId);
    }

    public boolean canMarkAttendance(PoolLesson lesson, AdminUser user) {
        if (user == null) return false;
        LocalDate today = LocalDate.now();
        if (lesson.getLessonDate().isAfter(today)) return false;
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(lesson.getLessonDate(), today);
        if (daysDiff > 14) return false;

        if (user.getRole() == AdminUser.Role.ADMIN) return true;
        if (user.getRole() == AdminUser.Role.COACH && lesson.getGroup().getTrainer() != null) {
            return lesson.getGroup().getTrainer().getId().equals(user.getId());
        }
        return false;
    }

    public List<Child> getEligibleChildren(Long groupId, LocalDate lessonDate) {
        LocalDateTime lessonDateTime = lessonDate.atStartOfDay();
        List<Long> eligibleChildIds = groupChildRepository.findByGroupId(groupId).stream()
                .filter(gc -> gc.getCreatedAt() != null && !gc.getCreatedAt().isAfter(lessonDateTime))
                .map(GroupChild::getChildId)
                .collect(Collectors.toList());
        if (eligibleChildIds.isEmpty()) return List.of();
        return childRepository.findAllById(eligibleChildIds);
    }

    // ВОССТАНОВЛЕННЫЙ МЕТОД ДЛЯ СОХРАНЕНИЯ ПОСЕЩАЕМОСТИ
    @Transactional
    public void saveAttendance(Long lessonId, Map<Long, String> marks, AdminUser marker) {
        for (Map.Entry<Long, String> entry : marks.entrySet()) {
            Long childId = entry.getKey();
            String statusStr = entry.getValue();
            try {
                Attendance.Status status = Attendance.Status.valueOf(statusStr);
                Attendance attendance = attendanceRepository
                        .findByLessonIdAndChildId(lessonId, childId)
                        .orElse(new Attendance());

                if (attendance.getId() == null) {
                    PoolLesson pl = new PoolLesson();
                    pl.setId(lessonId);
                    attendance.setLesson(pl);
                    Child ch = new Child();
                    ch.setId(childId);
                    attendance.setChild(ch);
                }
                attendance.setStatus(status);
                attendance.setMarkedBy(marker);
                attendance.setMarkedAt(LocalDateTime.now());
                attendanceRepository.save(attendance);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Transactional
    public void saveAttendanceWithComments(Long lessonId, Map<Long, String> marks,
                                           Map<Long, String> comments, AdminUser marker) {
        for (Map.Entry<Long, String> entry : marks.entrySet()) {
            Long childId = entry.getKey();
            String statusStr = entry.getValue();
            try {
                Attendance.Status status = Attendance.Status.valueOf(statusStr);
                Attendance attendance = attendanceRepository
                        .findByLessonIdAndChildId(lessonId, childId)
                        .orElse(new Attendance());
                if (attendance.getId() == null) {
                    PoolLesson pl = new PoolLesson();
                    pl.setId(lessonId);
                    attendance.setLesson(pl);
                    Child ch = new Child();
                    ch.setId(childId);
                    attendance.setChild(ch);
                }
                attendance.setStatus(status);
                attendance.setMarkedBy(marker);
                attendance.setMarkedAt(LocalDateTime.now());
                attendance.setComment(comments.getOrDefault(childId, null));
                attendanceRepository.save(attendance);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}