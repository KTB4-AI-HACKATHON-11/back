package com.ktb.hackathon.team11.attempt;

import com.ktb.hackathon.team11.ai.AiTaskClient;
import com.ktb.hackathon.team11.ai.PhotoCheckCommand;
import com.ktb.hackathon.team11.ai.PhotoCheckResult;
import com.ktb.hackathon.team11.ai.PhotoUnavailableException;
import com.ktb.hackathon.team11.assignment.AssignmentService;
import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.storage.FileStorage;
import com.ktb.hackathon.team11.storage.PhotoInspector;
import com.ktb.hackathon.team11.storage.StoredFile;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TaskAttemptService {
  private final TaskAttemptRepository attempts;
  private final AssignmentService assignments;
  private final GroupService groups;
  private final PhotoInspector inspector;
  private final FileStorage storage;
  private final AiTaskClient ai;
  private final TaskAttemptTransactionService transactions;

  @Value("${storage.presigned-url-minutes:5}") private long urlMinutes;

  public Result submit(long assignmentId, long workerId, MultipartFile file) {
    PhotoInspector.InspectedPhoto photo = inspector.inspect(file);
    TaskAttemptTransactionService.AttemptSnapshot snapshot =
        transactions.reserve(assignmentId, workerId, photo);

    StoredFile stored;
    try {
      stored = storage.store(snapshot.objectKey(), photo.bytes(), photo.mimeType());
    } catch (RuntimeException exception) {
      storage.delete(snapshot.objectKey());
      transactions.cancelReservation(snapshot.attemptId(), snapshot.previousStatus());
      throw exception;
    }

    try {
      transactions.attachPhoto(snapshot);
    } catch (RuntimeException exception) {
      storage.delete(snapshot.objectKey());
      transactions.cancelReservation(snapshot.attemptId(), snapshot.previousStatus());
      if (exception instanceof DataIntegrityViolationException)
        throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
      throw exception;
    }

    return Result.from(evaluate(snapshot, stored.url()));
  }

  public Result retry(long attemptId, long managerId) {
    TaskAttemptTransactionService.AttemptSnapshot snapshot =
        transactions.prepareRetry(attemptId, managerId);
    return Result.from(evaluate(snapshot, null));
  }

  private TaskAttemptTransactionService.AttemptState evaluate(
      TaskAttemptTransactionService.AttemptSnapshot snapshot, String submittedUrl) {
    try {
      if (submittedUrl == null)
        submittedUrl =
            storage.createReadUrl(snapshot.objectKey(), Duration.ofMinutes(urlMinutes));
      String referenceUrl =
          snapshot.hasReferenceImage()
              ? storage.createReadUrl(
                  snapshot.referenceImageKey(), Duration.ofMinutes(urlMinutes))
              : null;
      PhotoCheckResult result;
      try {
        result = check(snapshot, submittedUrl, referenceUrl);
      } catch (PhotoUnavailableException exception) {
        if (exception.isReferencePhoto()) {
          if (!snapshot.hasReferenceImage())
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
          referenceUrl =
              storage.createReadUrl(
                  snapshot.referenceImageKey(), Duration.ofMinutes(urlMinutes));
        } else {
          submittedUrl =
              storage.createReadUrl(snapshot.objectKey(), Duration.ofMinutes(urlMinutes));
        }
        result = check(snapshot, submittedUrl, referenceUrl);
      }
      return transactions.finalizeResult(snapshot.attemptId(), result);
    } catch (BusinessException exception) {
      if (exception.getErrorCode() == ErrorCode.AI_UNAVAILABLE
          || exception.getErrorCode() == ErrorCode.PHOTO_UNAVAILABLE) {
        return transactions.delay(snapshot.attemptId());
      }
      transactions.delay(snapshot.attemptId());
      throw exception;
    } catch (RuntimeException exception) {
      transactions.delay(snapshot.attemptId());
      throw exception;
    }
  }

  private PhotoCheckResult check(
      TaskAttemptTransactionService.AttemptSnapshot snapshot,
      String submittedUrl,
      String referenceUrl) {
    PhotoCheckCommand.PhotoResource submittedPhoto =
        new PhotoCheckCommand.PhotoResource(
            snapshot.mimeType(), snapshot.sizeBytes(), snapshot.sha256(), submittedUrl);
    PhotoCheckCommand.PhotoResource referencePhoto =
        snapshot.hasReferenceImage()
            ? new PhotoCheckCommand.PhotoResource(
                snapshot.referenceImageMimeType(),
                snapshot.referenceImageSizeBytes(),
                snapshot.referenceImageSha256(),
                referenceUrl)
            : null;
    return ai.checkPhoto(
        new PhotoCheckCommand(
            snapshot.title(),
            snapshot.instruction(),
            snapshot.rule(),
            submittedPhoto,
            referencePhoto));
  }

  @Transactional(readOnly = true)
  public List<Result> history(long assignmentId, long memberId) {
    var assignment = assignments.require(assignmentId);
    groups.requireMember(
        assignment.getSchedule().getTaskTemplate().getGroup().getId(), memberId);
    return attempts.findAllByAssignmentIdOrderByAttemptNumber(assignmentId).stream()
        .map(Result::from)
        .toList();
  }

  public record Result(
      Long attemptId,
      int attemptNumber,
      AttemptStatus status,
      AssignmentStatus assignmentStatus,
      String reason,
      String fix) {
    static Result from(TaskAttempt attempt) {
      return new Result(
          attempt.getId(),
          attempt.getAttemptNumber(),
          attempt.getStatus(),
          attempt.getAssignment().getStatus(),
          attempt.getReason(),
          attempt.getFixMessage());
    }

    static Result from(TaskAttemptTransactionService.AttemptState state) {
      return new Result(
          state.attemptId(),
          state.attemptNumber(),
          state.status(),
          state.assignmentStatus(),
          state.reason(),
          state.fix());
    }
  }
}
