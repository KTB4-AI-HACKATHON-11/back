package com.ktb.hackathon.team11.assignment;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
  boolean existsByScheduleIdAndTaskItemTemplateIdAndScheduledDate(Long s, Long i, LocalDate d);

  List<TaskAssignment> findAllByScheduleTaskTemplateGroupIdAndScheduledDate(Long g, LocalDate d);

  List<TaskAssignment> findAllByScheduledDateAndAssigneeId(LocalDate d, Long a);

  List<TaskAssignment> findAllByScheduledDateAndAssigneeIsNull(LocalDate d);
}
