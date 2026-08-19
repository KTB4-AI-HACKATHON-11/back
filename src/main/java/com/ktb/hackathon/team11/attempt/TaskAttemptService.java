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
    public TaskAttempt submit(long assignmentId, long workerId, MultipartFile file) {
        Member worker = members.requireRole(workerId, MemberRole.WORKER);
        TaskAssignment assignment = assignmentService.require(assignmentId);
        long groupId = assignment.getSchedule().getTaskTemplate().getGroup().getId();
        groups.requireMember(groupId, workerId);
        if (assignment.getAssignee() != null && !assignment.getAssignee().getId().equals(workerId)) throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
        if (assignment.getTaskItemTemplate().getCompletionType() != CompletionType.PHOTO) throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
        LocalDateTime now = LocalDateTime.now(clock);
        assignment.requireAvailable(now);
        PhotoInspector.InspectedPhoto photo = inspector.inspect(file);
        if (photos.existsByGroupIdAndSha256(groupId, photo.sha256())) throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        int number = (int) attempts.countByAssignmentId(assignmentId) + 1;
        TaskAttempt attempt = attempts.save(new TaskAttempt(assignment, worker, number, now));
        String key = "groups/" + groupId + "/assignments/" + assignmentId + "/attempts/" + UUID.randomUUID() + "." + photo.extension();
        StoredFile stored = storage.store(key, photo.bytes(), photo.mimeType());
        photos.save(new TaskPhoto(attempt, assignment.getSchedule().getTaskTemplate().getGroup(), key, photo.mimeType(), photo.sizeBytes(), photo.sha256()));
        assignment.verifying();
        evaluate(assignment, attempt, photo.mimeType(), photo.sizeBytes(), photo.sha256(), key, stored.url());
        return attempt;
    }

    private void evaluate(TaskAssignment assignment, TaskAttempt attempt, String mime, long size, String sha, String objectKey, String url) {
        try {
            PhotoCheckResult result;
            try {
                result = check(assignment, mime, size, sha, url);
            } catch (BusinessException exception) {
                if (exception.getErrorCode() != ErrorCode.PHOTO_UNAVAILABLE) throw exception;
                result = check(assignment, mime, size, sha, storage.createReadUrl(objectKey, Duration.ofMinutes(urlMinutes)));
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

    private PhotoCheckResult check(TaskAssignment assignment, String mime, long size, String sha, String url) {
        var item = assignment.getTaskItemTemplate();
        return ai.checkPhoto(new PhotoCheckCommand(item.getTitle(), item.getInstruction(), item.getVerificationRule(), mime, size, sha, url));
    }

    public List<TaskAttempt> history(long assignmentId, long memberId) {
        TaskAssignment assignment = assignmentService.require(assignmentId);
        groups.requireMember(assignment.getSchedule().getTaskTemplate().getGroup().getId(), memberId);
        return attempts.findAllByAssignmentIdOrderByAttemptNumber(assignmentId);
    }

    @Transactional
    public TaskAttempt retry(long attemptId, long managerId) {
        TaskAttempt attempt = attempts.findById(attemptId).orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));
        TaskAssignment assignment = attempt.getAssignment();
        groups.requireManager(assignment.getSchedule().getTaskTemplate().getGroup().getId(), managerId);
        if (attempt.getStatus() != AttemptStatus.DELAYED) throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
        TaskPhoto photo = photos.findByAttemptId(attemptId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PHOTO));
        attempt.verifying();
        assignment.verifying();
        String url = storage.createReadUrl(photo.getObjectKey(), Duration.ofMinutes(urlMinutes));
        evaluate(assignment, attempt, photo.getMimeType(), photo.getSizeBytes(), photo.getSha256(), photo.getObjectKey(), url);
        return attempt;
    }
}
