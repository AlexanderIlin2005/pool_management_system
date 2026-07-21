package ru.sashil.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.admin.model.SchoolVacation;
import java.util.List;

public interface SchoolVacationRepository extends JpaRepository<SchoolVacation, Long> {
    List<SchoolVacation> findAllByOrderByStartDateAsc(); 
}