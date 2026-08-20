package com.ktb.hackathon.team11.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttemptRepository extends JpaRepository<TaskAttempt, Long> {
  long countByAssignmentId(Long id);

  List<TaskAttempt> findAllByAssignmentIdOrderByAttemptNumber(Long id);

  List<TaskAttempt> findAllByAssignmentIdInOrderByAssignmentIdAscAttemptNumberDesc(
      Collection<Long> assignmentIds);

  Optional<TaskAttempt> findFirstByAssignmentIdOrderByAttemptNumberDesc(Long id);

  List<TaskAttempt> findTop20ByStatusAndUpdatedAtBeforeOrderByIdAsc(
      AttemptStatus status, LocalDateTime updatedAt);
}
