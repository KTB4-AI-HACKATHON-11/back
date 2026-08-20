package com.ktb.hackathon.team11.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskScheduleRepository extends JpaRepository<TaskSchedule, Long> {
  List<TaskSchedule> findAllByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      LocalDate d1, LocalDate d2);

  List<TaskSchedule> findAllByActiveTrueAndStartDateLessThanEqualAndEndDateIsNull(LocalDate d);

  List<TaskSchedule>
      findAllByTaskTemplateGroupIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
          long groupId, LocalDate d1, LocalDate d2);

  List<TaskSchedule>
      findAllByTaskTemplateGroupIdAndActiveTrueAndStartDateLessThanEqualAndEndDateIsNull(
          long groupId, LocalDate date);

  Optional<TaskSchedule> findFirstByTaskTemplateIdOrderByIdDesc(Long taskTemplateId);

  List<TaskSchedule> findAllByTaskTemplateId(Long taskTemplateId);
}
