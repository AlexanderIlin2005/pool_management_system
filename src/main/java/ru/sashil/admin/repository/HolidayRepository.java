package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.Holiday;
import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
    boolean existsByHolidayDate(LocalDate date);
}