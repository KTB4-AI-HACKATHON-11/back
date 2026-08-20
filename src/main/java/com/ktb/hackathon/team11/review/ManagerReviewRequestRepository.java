package com.ktb.hackathon.team11.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerReviewRequestRepository
    extends JpaRepository<ManagerReviewRequest, Long> {
  boolean existsByAssignmentIdAndStatus(Long assignmentId, ManagerReviewStatus status);

  Optional<ManagerReviewRequest> findFirstByAssignmentIdOrderByRequestedAtDesc(Long assignmentId);

  Optional<ManagerReviewRequest> findFirstByAssignmentIdAndStatus(
      Long assignmentId, ManagerReviewStatus status);

  @EntityGraph(
      attributePaths = {
        "assignment",
        "assignment.schedule",
        "assignment.schedule.taskTemplate",
        "assignment.taskItemTemplate",
        "attempt",
        "requester"
      })
  List<ManagerReviewRequest> findAllByGroupIdAndStatusOrderByRequestedAtAsc(
      Long groupId, ManagerReviewStatus status);
}
