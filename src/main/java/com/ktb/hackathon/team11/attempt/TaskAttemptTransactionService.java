package com.ktb.hackathon.team11.attempt;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.ai.PhotoCheckResult;
import com.ktb.hackathon.team11.ai.PhotoCheckStatus;
import com.ktb.hackathon.team11.assignment.AssignmentService;
import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.notification.CompletionNotificationService;
import com.ktb.hackathon.team11.storage.PhotoInspector;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskAttemptTransactionService {
  private final TaskAttemptRepository attempts;
  private final TaskPhotoRepository photos;
  private final AssignmentService assignments;
  private final GroupService groups;
  private final CompletionNotificationService completionNotifications;
  private final Clock clock;

  @Transactional
  public AttemptSnapshot reserve(
      long assignmentId, long workerId, PhotoInspector.InspectedPhoto photo) {
    TaskAssignment assignment = assignments.requireForUpdate(assignmentId);
    long groupId = assignment.getSchedule().getTaskTemplate().getGroup().getId();
    Member worker = groups.requireWorker(groupId, workerId).getMember();
    if (assignment.getAssignee() != null && !assignment.getAssignee().getId().equals(workerId))
      throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
    if (assignment.getTaskItemTemplate().getCompletionType() != CompletionType.PHOTO)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    assignment.requirePhotoSubmissionAvailable(LocalDateTime.now(clock));
    if (photos.existsByGroupIdAndSha256(groupId, photo.sha256()))
      throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);

    AssignmentStatus previousStatus = assignment.getStatus();
    int number = (int) attempts.countByAssignmentId(assignmentId) + 1;
    TaskAttempt attempt =
        attempts.save(
            new TaskAttempt(assignment, worker, number, LocalDateTime.now(clock)));
    assignment.verifying();
    String objectKey =
        "groups/"
            + groupId
            + "/assignments/"
            + assignmentId
            + "/attempts/"
            + UUID.randomUUID()
            + "."
            + photo.extension();
    return snapshot(attempt, objectKey, photo, previousStatus);
  }

  @Transactional
  public void attachPhoto(AttemptSnapshot snapshot) {
    TaskAttempt attempt = require(snapshot.attemptId());
    TaskAssignment assignment = attempt.getAssignment();
    photos.save(
        new TaskPhoto(
            attempt,
            assignment.getSchedule().getTaskTemplate().getGroup(),
            snapshot.objectKey(),
            snapshot.mimeType(),
            snapshot.sizeBytes(),
            snapshot.sha256()));
  }

  @Transactional
  public void cancelReservation(long attemptId, AssignmentStatus previousStatus) {
    TaskAttempt attempt = attempts.findById(attemptId).orElse(null);
    if (attempt == null) return;
    TaskAssignment assignment = assignments.requireForUpdate(attempt.getAssignment().getId());
    if (attempt.getStatus() == AttemptStatus.VERIFYING) {
      attempts.delete(attempt);
      assignment.restorePhotoSubmission(previousStatus);
    }
  }

  @Transactional
  public AttemptSnapshot prepareRetry(long attemptId, long managerId) {
    TaskAttempt attempt = require(attemptId);
    TaskAssignment assignment = assignments.requireForUpdate(attempt.getAssignment().getId());
    groups.requireManager(
        assignment.getSchedule().getTaskTemplate().getGroup().getId(), managerId);
    if (attempt.getStatus() != AttemptStatus.DELAYED
        || assignment.getStatus() != AssignmentStatus.VERIFICATION_DELAYED)
      throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
    TaskPhoto photo =
        photos
            .findByAttemptId(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PHOTO));
    attempt.verifying();
    assignment.verifying();
    return snapshot(attempt, photo, AssignmentStatus.VERIFICATION_DELAYED);
  }

  @Transactional
  public AttemptState finalizeResult(long attemptId, PhotoCheckResult result) {
    TaskAttempt attempt = require(attemptId);
    TaskAssignment assignment = assignments.requireForUpdate(attempt.getAssignment().getId());
    if (result.status() == PhotoCheckStatus.PASS) {
      attempt.pass(result.reason());
      assignment.pass(LocalDateTime.now(clock));
      completionNotifications.afterStateChange(assignment);
    } else {
      attempt.retake(result.reason(), result.fix());
      assignment.retake();
    }
    return state(attempt);
  }

  @Transactional
  public AttemptState delay(long attemptId) {
    TaskAttempt attempt = require(attemptId);
    TaskAssignment assignment = assignments.requireForUpdate(attempt.getAssignment().getId());
    attempt.delayed();
    assignment.delayed();
    return state(attempt);
  }

  @Transactional(readOnly = true)
  public AttemptState state(long attemptId) {
    return state(require(attemptId));
  }

  private TaskAttempt require(long attemptId) {
    return attempts
        .findById(attemptId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));
  }

  private AttemptSnapshot snapshot(
      TaskAttempt attempt,
      String objectKey,
      PhotoInspector.InspectedPhoto photo,
      AssignmentStatus previousStatus) {
    var item = attempt.getAssignment().getTaskItemTemplate();
    return new AttemptSnapshot(
        attempt.getId(),
        attempt.getAssignment().getId(),
        attempt.getAssignment().getSchedule().getTaskTemplate().getGroup().getId(),
        objectKey,
        photo.mimeType(),
        photo.sizeBytes(),
        photo.sha256(),
        item.getTitle(),
        item.getInstruction(),
        item.getVerificationRule(),
        item.getReferenceImageKey(),
        item.getReferenceImageMimeType(),
        item.getReferenceImageSizeBytes(),
        item.getReferenceImageSha256(),
        previousStatus);
  }

  private AttemptSnapshot snapshot(
      TaskAttempt attempt, TaskPhoto photo, AssignmentStatus previousStatus) {
    var item = attempt.getAssignment().getTaskItemTemplate();
    return new AttemptSnapshot(
        attempt.getId(),
        attempt.getAssignment().getId(),
        attempt.getAssignment().getSchedule().getTaskTemplate().getGroup().getId(),
        photo.getObjectKey(),
        photo.getMimeType(),
        photo.getSizeBytes(),
        photo.getSha256(),
        item.getTitle(),
        item.getInstruction(),
        item.getVerificationRule(),
        item.getReferenceImageKey(),
        item.getReferenceImageMimeType(),
        item.getReferenceImageSizeBytes(),
        item.getReferenceImageSha256(),
        previousStatus);
  }

  private AttemptState state(TaskAttempt attempt) {
    return new AttemptState(
        attempt.getId(),
        attempt.getAttemptNumber(),
        attempt.getStatus(),
        attempt.getAssignment().getStatus(),
        attempt.getReason(),
        attempt.getFixMessage());
  }

  public record AttemptSnapshot(
      long attemptId,
      long assignmentId,
      long groupId,
      String objectKey,
      String mimeType,
      long sizeBytes,
      String sha256,
      String title,
      String instruction,
      String rule,
      String referenceImageKey,
      String referenceImageMimeType,
      Long referenceImageSizeBytes,
      String referenceImageSha256,
      AssignmentStatus previousStatus) {
    public boolean hasReferenceImage() {
      return referenceImageKey != null && !referenceImageKey.isBlank();
    }
  }

  public record AttemptState(
      Long attemptId,
      int attemptNumber,
      AttemptStatus status,
      AssignmentStatus assignmentStatus,
      String reason,
      String fix) {}
}
