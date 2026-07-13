package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sashil.admin.model.PoolLesson;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface PoolLessonRepository extends JpaRepository<PoolLesson, Long> {

    List<PoolLesson> findByGroupIdAndLessonDateBetweenOrderByStartTime(Long groupId, LocalDate start, LocalDate end);

    Optional<PoolLesson> findByGroupIdAndLessonDateAndStartTime(Long groupId, LocalDate date, java.time.LocalTime time);

    // Проверка существования занятия до определенной даты
    boolean existsByGroupIdAndLessonDateLessThan(Long groupId, LocalDate date);
    boolean existsByGroupIdAndLessonDateAndStartTime(Long groupId, LocalDate date, LocalTime startTime);

    // <-- ДОБАВЬ ЭТОТ МЕТОД
    List<PoolLesson> findByGroupIdInAndLessonDateBetweenOrderByStartTime(List<Long> groupIds, LocalDate start, LocalDate end);

    Optional<PoolLesson> findByGroupIdAndLessonDate(Long groupId, LocalDate date);
}