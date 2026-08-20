package com.ktb.hackathon.team11.review;

import com.ktb.hackathon.team11.assignment.AssignmentService;
import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.attempt.AttemptStatus;
import com.ktb.hackathon.team11.attempt.TaskAttempt;
import com.ktb.hackathon.team11.attempt.TaskAttemptRepository;
import com.ktb.hackathon.team11.attempt.TaskPhotoRepository;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.notification.CompletionNotificationService;
import com.ktb.hackathon.team11.notification.ManagerReviewNotificationService;
import com.ktb.hackathon.team11.storage.FileStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagerReviewService {
  private static final int MINIMUM_ATTEMPTS = 3;
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

  private final ManagerReviewRequestRepository reviews;
  private final TaskAttemptRepository attempts;
  private final TaskPhotoRepository photos;
  private final AssignmentService assignments;
  private final GroupService groups;
  private final FileStorage storage;
  private final ManagerReviewNotificationService reviewNotifications;
  private final CompletionNotificationService completionNotifications;
  private final Clock clock;

  @Value("${storage.presigned-url-minutes:5}") private long urlMinutes;

  @Transactional
  public Response request(long assignmentId, long workerId) {
    TaskAssignment assignment = assignments.requireForUpdate(assignmentId);
    long groupId = assignment.getSchedule().getTaskTemplate().getGroup().getId();
    Member worker = groups.requireWorker(groupId, workerId).getMember();
    if (assignment.getAssignee() != null && !assignment.getAssignee().getId().equals(workerId))
      throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);

    var pending =
        reviews.findFirstByAssignmentIdAndStatus(assignmentId, ManagerReviewStatus.PENDING);
    if (pending.isPresent()) return response(pending.get());

    TaskAttempt latest =
        attempts
            .findFirstByAssignmentIdOrderByAttemptNumberDesc(assignmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MANAGER_REVIEW_NOT_AVAILABLE));
    if (assignment.getStatus() != AssignmentStatus.RETAKE_REQUIRED
        || latest.getStatus() != AttemptStatus.RETAKE
        || attempts.countByAssignmentId(assignmentId) < MINIMUM_ATTEMPTS)
      throw new BusinessException(ErrorCode.MANAGER_REVIEW_NOT_AVAILABLE);

    assignment.requestManagerReview();
    ManagerReviewRequest saved =
        reviews.save(
            new ManagerReviewRequest(assignment, latest, worker, LocalDateTime.now(clock)));
    reviewNotifications.requested(assignment);
    return response(saved);
  }

  public List<Response> list(long groupId, long managerId, ManagerReviewStatus status) {
    groups.requireManager(groupId, managerId);
    ManagerReviewStatus filter = status == null ? ManagerReviewStatus.PENDING : status;
    return reviews.findAllByGroupIdAndStatusOrderByRequestedAtAsc(groupId, filter).stream()
        .map(this::response)
        .toList();
  }

  @Transactional
  public Response resolve(
      long reviewId, long managerId, ManagerReviewDecision decision, String message) {
    ManagerReviewRequest review = require(reviewId);
    TaskAssignment assignment = assignments.requireForUpdate(review.getAssignment().getId());
    Member manager =
        groups
            .requireManager(
                assignment.getSchedule().getTaskTemplate().getGroup().getId(), managerId)
            .getMember();
    if (assignment.getStatus() != AssignmentStatus.MANAGER_REVIEW_REQUESTED)
      throw new BusinessException(ErrorCode.MANAGER_REVIEW_ALREADY_RESOLVED);
    LocalDateTime now = LocalDateTime.now(clock);
    review.resolve(decision, manager, message, now);
    if (decision == ManagerReviewDecision.APPROVE) {
      assignment.pass(now);
      completionNotifications.afterStateChange(assignment);
    } else {
      assignment.retake();
    }
    return response(review);
  }

  private ManagerReviewRequest require(long reviewId) {
    return reviews
        .findById(reviewId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MANAGER_REVIEW_NOT_FOUND));
  }

  private Response response(ManagerReviewRequest review) {
    TaskAssignment assignment = review.getAssignment();
    TaskAttempt attempt = review.getAttempt();
    String photoUrl =
        photos
            .findByAttemptId(attempt.getId())
            .map(
                photo ->
                    storage.createReadUrl(
                        photo.getObjectKey(), Duration.ofMinutes(urlMinutes)))
            .orElse(null);
    return new Response(
        review.getId(),
        assignment.getId(),
        assignment.getSchedule().getTaskTemplate().getId(),
        assignment.getSchedule().getTaskTemplate().getTitle(),
        assignment.getTaskItemTemplate().getTitle(),
        attempt.getId(),
        attempt.getAttemptNumber(),
        review.getRequester().getId(),
        review.getRequester().getNickname(),
        review.getStatus(),
        review.getMessage(),
        photoUrl,
        offset(review.getRequestedAt()),
        offset(review.getResolvedAt()));
  }

  private OffsetDateTime offset(LocalDateTime value) {
    return value == null ? null : value.atZone(SERVICE_ZONE).toOffsetDateTime();
  }

  public record Response(
      Long reviewId,
      Long assignmentId,
      Long taskId,
      String taskTitle,
      String checklistTitle,
      Long attemptId,
      int attemptNumber,
      Long workerId,
      String workerNickname,
      ManagerReviewStatus status,
      String message,
      String photoUrl,
      OffsetDateTime requestedAt,
      OffsetDateTime resolvedAt) {}
}
