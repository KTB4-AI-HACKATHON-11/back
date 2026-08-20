package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.assignment.TaskAssignmentRepository;
import com.ktb.hackathon.team11.attempt.TaskAttempt;
import com.ktb.hackathon.team11.attempt.TaskAttemptRepository;
import com.ktb.hackathon.team11.attempt.TaskPhoto;
import com.ktb.hackathon.team11.attempt.TaskPhotoRepository;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.MemberRole;
import com.ktb.hackathon.team11.storage.FileStorage;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryService {
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

  private final TaskTemplateRepository templates;
  private final TaskAssignmentRepository assignments;
  private final TaskAttemptRepository attempts;
  private final TaskPhotoRepository photos;
  private final GroupService groups;
  private final FileStorage storage;
  private final Clock clock;

  @Value("${storage.presigned-url-minutes:5}") private long urlMinutes;

  public TaskListResponse list(long groupId, long requesterId, int offset, int limit, TaskStatus filter) {
    groups.requireMember(groupId, requesterId);
    List<TaskSummary> all =
        templates.findAllByGroupIdAndActiveTrueOrderByCreatedAtDesc(groupId).stream()
            .map(this::summary)
            .filter(summary -> summary.itemCount() > 0)
            .filter(summary -> filter == null || summary.status() == filter)
            .toList();

    int from = Math.min(offset, all.size());
    int to = Math.min(from + limit, all.size());
    return new TaskListResponse(all.size(), all.subList(from, to));
  }

  public TaskDetail detail(long taskId, long requesterId) {
    TaskTemplate template =
        templates.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    groups.requireMember(template.getGroup().getId(), requesterId);
    List<TaskAssignment> taskAssignments = taskAssignments(taskId);
    if (taskAssignments.isEmpty()) throw new BusinessException(ErrorCode.TASK_NOT_FOUND);

    TaskSummary summary = summary(template, taskAssignments);
    return new TaskDetail(
        taskId,
        template.getGroup().getId(),
        template.getTitle(),
        template.getSourceMessage(),
        template.getCreator().getId(),
        template.getCreator().getNickname(),
        taskAssignments.get(0).getAssignee() == null
            ? null
            : taskAssignments.get(0).getAssignee().getId(),
        taskAssignments.get(0).getAssignee() == null
            ? null
            : taskAssignments.get(0).getAssignee().getNickname(),
        toOffsetDateTime(taskAssignments.get(0).getDueAt()),
        summary.status(),
        summary.progress(),
        toOffsetDateTime(template.getCreatedAt()),
        taskAssignments.stream().sorted(Comparator.comparing(a -> a.getTaskItemTemplate().getSequence()))
            .map(this::checklist)
            .toList());
  }

  private TaskSummary summary(TaskTemplate template) {
    return summary(template, taskAssignments(template.getId()));
  }

  private TaskSummary summary(TaskTemplate template, List<TaskAssignment> taskAssignments) {
    int itemCount = taskAssignments.size();
    int completed = (int) taskAssignments.stream().filter(this::performed).count();
    return new TaskSummary(
        template.getId(),
        template.getTitle(),
        taskAssignments.isEmpty() ? null : taskAssignments.get(0).getAssignee(),
        taskAssignments.isEmpty() ? null : toOffsetDateTime(taskAssignments.get(0).getDueAt()),
        status(taskAssignments),
        itemCount,
        completed,
        progress(completed, itemCount),
        taskAssignments.stream()
            .anyMatch(a -> a.getTaskItemTemplate().getCompletionType() == com.ktb.hackathon.team11.ai.CompletionType.PHOTO));
  }

  private Checklist checklist(TaskAssignment assignment) {
    TaskItemTemplate item = assignment.getTaskItemTemplate();
    TaskAttempt attempt = attempts.findFirstByAssignmentIdOrderByAttemptNumberDesc(assignment.getId()).orElse(null);
    TaskPhoto photo = attempt == null ? null : photos.findByAttemptId(attempt.getId()).orElse(null);
    return new Checklist(
        assignment.getId(),
        item.getSequence(),
        item.getTitle(),
        item.getInstruction(),
        item.getCompletionType(),
        item.getVerificationRule(),
        item.hasReferenceImage() ? storage.createReadUrl(item.getReferenceImageKey(), Duration.ofMinutes(urlMinutes)) : null,
        performed(assignment),
        toOffsetDateTime(assignment.getCompletedAt()),
        photo == null ? null : storage.createReadUrl(photo.getObjectKey(), Duration.ofMinutes(urlMinutes)));
  }

  private List<TaskAssignment> taskAssignments(long taskId) {
    return assignments.findAllByScheduleTaskTemplateId(taskId).stream()
        .sorted(Comparator.comparing(a -> a.getTaskItemTemplate().getSequence()))
        .toList();
  }

  private boolean performed(TaskAssignment assignment) {
    return assignment.getStatus() == AssignmentStatus.COMPLETED;
  }

  private TaskStatus status(List<TaskAssignment> taskAssignments) {
    if (taskAssignments.isEmpty()) return TaskStatus.WAITING;
    if (taskAssignments.stream().allMatch(this::performed)) return TaskStatus.COMPLETED;
    LocalDateTime now = LocalDateTime.now(clock);
    if (taskAssignments.stream().allMatch(a -> now.isBefore(a.getAvailableFrom()))) return TaskStatus.WAITING;
    if (taskAssignments.stream().allMatch(a -> now.isAfter(a.getDueAt()) || a.getStatus() == AssignmentStatus.EXPIRED))
      return TaskStatus.OVERDUE;
    return TaskStatus.IN_PROGRESS;
  }

  private int progress(int completed, int total) {
    return total == 0 ? 0 : Math.round(completed * 100f / total);
  }

  private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
    return value == null ? null : value.atZone(SERVICE_ZONE).toOffsetDateTime();
  }

  public record TaskListResponse(int totalCount, List<TaskSummary> items) {}

  public record TaskSummary(
      Long taskId,
      String title,
      Long workerId,
      String workerNickname,
      OffsetDateTime dueAt,
      TaskStatus status,
      int itemCount,
      int completedItemCount,
      int progress,
      boolean hasPhotoVerification) {
    private TaskSummary(Long taskId, String title, com.ktb.hackathon.team11.member.Member worker,
        OffsetDateTime dueAt, TaskStatus status, int itemCount, int completedItemCount,
        int progress, boolean hasPhotoVerification) {
      this(taskId, title, worker == null ? null : worker.getId(), worker == null ? null : worker.getNickname(),
          dueAt, status, itemCount, completedItemCount, progress, hasPhotoVerification);
    }
  }

  public record TaskDetail(
      Long taskId,
      Long groupId,
      String title,
      String message,
      Long managerId,
      String managerNickname,
      Long workerId,
      String workerNickname,
      OffsetDateTime dueAt,
      TaskStatus status,
      int progress,
      OffsetDateTime createdAt,
      List<Checklist> checklists) {}

  public record Checklist(
      Long checklistId,
      int sequence,
      String title,
      String instruction,
      com.ktb.hackathon.team11.ai.CompletionType completionType,
      String rule,
      String referencePhotoUrl,
      boolean performed,
      OffsetDateTime performedAt,
      String submittedPhotoUrl) {}
}
