package com.ktb.hackathon.team11.attempt;

import com.ktb.hackathon.team11.ai.*;
import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskAttemptService {
    private final TaskAttemptRepository attempts;
    private final TaskPhotoRepository photos;
    private final AssignmentService assignmentService;
    private final MemberService members;
    private final GroupService groups;
    private final PhotoInspector inspector;
    private final FileStorage storage;
    private final AiTaskClient ai;
    private final Clock clock;
    @Value("${storage.presigned-url-minutes:5}") private long urlMinutes;

    @Transactional
    public Result submit(long assignmentId, long workerId, MultipartFile file) {
        Member worker = members.requireRole(workerId, MemberRole.WORKER);
        TaskAssignment assignment = assignmentService.requireForUpdate(assignmentId);
        long groupId = assignment.getSchedule().getTaskTemplate().getGroup().getId();
        groups.requireMember(groupId, workerId);
        if (assignment.getAssignee() != null && !assignment.getAssignee().getId().equals(workerId)) throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
        if (assignment.getTaskItemTemplate().getCompletionType() != CompletionType.PHOTO) throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
        LocalDateTime now = LocalDateTime.now(clock);
        assignment.requirePhotoSubmissionAvailable(now);
        PhotoInspector.InspectedPhoto photo = inspector.inspect(file);
        if (photos.existsByGroupIdAndSha256(groupId, photo.sha256())) throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        int number = (int) attempts.countByAssignmentId(assignmentId) + 1;
        TaskAttempt attempt = attempts.save(new TaskAttempt(assignment, worker, number, now));
        String key = "groups/" + groupId + "/assignments/" + assignmentId + "/attempts/" + UUID.randomUUID() + "." + photo.extension();
        List<String> rollbackKeys = new ArrayList<>();
        rollbackKeys.add(key);
        registerRollbackCleanup(rollbackKeys);
        StoredFile stored = storage.store(key, photo.bytes(), photo.mimeType());
        photos.save(new TaskPhoto(attempt, assignment.getSchedule().getTaskTemplate().getGroup(), key, photo.mimeType(), photo.sizeBytes(), photo.sha256()));
        assignment.verifying();
        evaluate(assignment, attempt, photo.mimeType(), photo.sizeBytes(), photo.sha256(), key, stored.url());
        return Result.from(attempt);
    }

    private void evaluate(TaskAssignment assignment, TaskAttempt attempt, String mime, long size, String sha, String objectKey, String url) {
        try {
            var item = assignment.getTaskItemTemplate();
            if (!item.hasReferenceImage())
                throw new BusinessException(ErrorCode.REFERENCE_PHOTO_REQUIRED);
            String referenceUrl = storage.createReadUrl(item.getReferenceImageKey(), Duration.ofMinutes(urlMinutes));
            PhotoCheckResult result;
            try {
                result = check(assignment, mime, size, sha, url, referenceUrl);
            } catch (PhotoUnavailableException exception) {
                if (exception.isReferencePhoto() && item.hasReferenceImage()) {
                    referenceUrl = storage.createReadUrl(item.getReferenceImageKey(), Duration.ofMinutes(urlMinutes));
                } else {
                    url = storage.createReadUrl(objectKey, Duration.ofMinutes(urlMinutes));
                }
                result = check(assignment, mime, size, sha, url, referenceUrl);
            }
            if (result.status() == PhotoCheckStatus.PASS) {
                attempt.pass(result.reason());
                assignment.pass(LocalDateTime.now(clock));
            } else {
                attempt.retake(result.reason(), result.fix());
                assignment.retake();
            }
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.AI_UNAVAILABLE && exception.getErrorCode() != ErrorCode.PHOTO_UNAVAILABLE) throw exception;
            attempt.delayed();
            assignment.delayed();
        }
    }

    private PhotoCheckResult check(TaskAssignment assignment, String mime, long size, String sha, String url, String referenceUrl) {
        var item = assignment.getTaskItemTemplate();
        PhotoCheckCommand.PhotoResource submittedPhoto = new PhotoCheckCommand.PhotoResource(mime, size, sha, url);
        PhotoCheckCommand.PhotoResource referencePhoto = item.hasReferenceImage()
                ? new PhotoCheckCommand.PhotoResource(
                        item.getReferenceImageMimeType(),
                        item.getReferenceImageSizeBytes(),
                        item.getReferenceImageSha256(),
                        referenceUrl)
                : null;
        return ai.checkPhoto(new PhotoCheckCommand(
                item.getTitle(),
                item.getInstruction(),
                item.getVerificationRule(),
                submittedPhoto,
                referencePhoto));
    }

    public List<Result> history(long assignmentId, long memberId) {
        TaskAssignment assignment = assignmentService.require(assignmentId);
        groups.requireMember(assignment.getSchedule().getTaskTemplate().getGroup().getId(), memberId);
        return attempts.findAllByAssignmentIdOrderByAttemptNumber(assignmentId).stream()
                .map(Result::from)
                .toList();
    }

    @Transactional
    public Result retry(long attemptId, long managerId) {
        TaskAttempt attempt = attempts.findById(attemptId).orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));
        TaskAssignment assignment = assignmentService.requireForUpdate(attempt.getAssignment().getId());
        groups.requireManager(assignment.getSchedule().getTaskTemplate().getGroup().getId(), managerId);
        if (attempt.getStatus() != AttemptStatus.DELAYED || assignment.getStatus() != AssignmentStatus.VERIFICATION_DELAYED) throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
        TaskPhoto photo = photos.findByAttemptId(attemptId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PHOTO));
        attempt.verifying();
        assignment.verifying();
        String url = storage.createReadUrl(photo.getObjectKey(), Duration.ofMinutes(urlMinutes));
        evaluate(assignment, attempt, photo.getMimeType(), photo.getSizeBytes(), photo.getSha256(), photo.getObjectKey(), url);
        return Result.from(attempt);
    }

    private void registerRollbackCleanup(List<String> objectKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) objectKeys.forEach(storage::delete);
            }
        });
    }

    public record Result(
            Long attemptId,
            int attemptNumber,
            AttemptStatus status,
            AssignmentStatus assignmentStatus,
            String reason,
            String fix
    ) {
        static Result from(TaskAttempt attempt) {
            return new Result(
                    attempt.getId(),
                    attempt.getAttemptNumber(),
                    attempt.getStatus(),
                    attempt.getAssignment().getStatus(),
                    attempt.getReason(),
                    attempt.getFixMessage());
        }
    }
}
