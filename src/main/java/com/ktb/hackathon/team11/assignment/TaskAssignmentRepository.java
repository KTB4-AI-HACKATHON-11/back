package com.ktb.hackathon.team11.assignment;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from TaskAssignment a where a.id = :id")
  Optional<TaskAssignment> findByIdForUpdate(@Param("id") Long id);

  boolean existsByScheduleIdAndTaskItemTemplateIdAndScheduledDate(Long s, Long i, LocalDate d);

  List<TaskAssignment> findAllByScheduleTaskTemplateGroupIdAndScheduledDate(Long g, LocalDate d);

  List<TaskAssignment> findAllByScheduledDateAndAssigneeId(LocalDate d, Long a);

  List<TaskAssignment> findAllByScheduledDateAndAssigneeIsNull(LocalDate d);

  List<TaskAssignment> findAllByScheduleTaskTemplateId(Long templateId);

  Optional<TaskAssignment> findByIdAndScheduleTaskTemplateId(Long id, Long templateId);

  long countByScheduleTaskTemplateGroupId(Long groupId);

  long countByScheduleTaskTemplateGroupIdAndStatus(
      Long groupId, AssignmentStatus status);
}
