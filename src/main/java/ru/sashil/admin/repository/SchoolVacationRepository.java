package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sashil.admin.model.SchoolVacation;
import java.time.LocalDate;
import java.util.List;

public interface SchoolVacationRepository extends JpaRepository<SchoolVacation, Long> {
    @Query("SELECT v FROM SchoolVacation v WHERE :date BETWEEN v.startDate AND v.endDate")
    List<SchoolVacation> findActiveOnDate(LocalDate date);

    List<SchoolVacation> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate start, LocalDate end);
}