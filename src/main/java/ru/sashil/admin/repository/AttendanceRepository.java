package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.Attendance;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByLessonId(Long lessonId);
    Optional<Attendance> findByLessonIdAndChildId(Long lessonId, Long childId);
}