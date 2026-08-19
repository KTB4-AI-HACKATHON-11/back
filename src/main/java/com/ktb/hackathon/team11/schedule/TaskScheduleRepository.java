package com.ktb.hackathon.team11.schedule;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskScheduleRepository extends JpaRepository<TaskSchedule, Long> {
  List<TaskSchedule> findAllByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      LocalDate d1, LocalDate d2);

  List<TaskSchedule> findAllByActiveTrueAndStartDateLessThanEqualAndEndDateIsNull(LocalDate d);
}
