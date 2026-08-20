package com.ktb.hackathon.team11.attempt;

import com.ktb.hackathon.team11.assignment.AssignmentService;
import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskAttemptRecoveryService {
  private final TaskAttemptRepository attempts;
  private final TaskPhotoRepository photos;
  private final AssignmentService assignments;
  private final Clock clock;

  @Value("${task.photo-verification-stale-seconds:120}") private long staleSeconds;

  @Scheduled(fixedDelayString = "${task.photo-verification-recovery-delay-ms:30000}")
  @Transactional
  public void recoverStaleAttempts() {
    LocalDateTime cutoff = LocalDateTime.now(clock).minusSeconds(staleSeconds);
    attempts
        .findTop20ByStatusAndUpdatedAtBeforeOrderByIdAsc(AttemptStatus.VERIFYING, cutoff)
        .forEach(
            attempt -> {
              var assignment = assignments.requireForUpdate(attempt.getAssignment().getId());
              if (photos.findByAttemptId(attempt.getId()).isPresent()) {
                attempt.delayed();
                assignment.delayed();
              } else {
                attempts.delete(attempt);
                assignment.restorePhotoSubmission(
                    attempt.getAttemptNumber() > 1
                        ? AssignmentStatus.RETAKE_REQUIRED
                        : AssignmentStatus.PENDING);
              }
            });
  }
}
