package com.ktb.hackathon.team11.attempt;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttemptRepository extends JpaRepository<TaskAttempt, Long> {
  long countByAssignmentId(Long id);

  List<TaskAttempt> findAllByAssignmentIdOrderByAttemptNumber(Long id);

  List<TaskAttempt> findAllByAssignmentIdInOrderByAssignmentIdAscAttemptNumberDesc(
      Collection<Long> assignmentIds);

  Optional<TaskAttempt> findFirstByAssignmentIdOrderByAttemptNumberDesc(Long id);
}
