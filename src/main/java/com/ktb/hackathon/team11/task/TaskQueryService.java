package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.assignment.TaskAssignmentRepository;
import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.attempt.TaskAttempt;
import com.ktb.hackathon.team11.attempt.TaskAttemptRepository;
import com.ktb.hackathon.team11.attempt.TaskPhoto;
import com.ktb.hackathon.team11.attempt.TaskPhotoRepository;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.storage.FileStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    Map<Long, List<TaskAssignment>> assignmentsByTemplate =
        assignments.findAllByScheduleTaskTemplateGroupId(groupId).stream()
            .collect(Collectors.groupingBy(this::templateId));
    List<TaskSummary> all =
        templates.findAllByGroupIdAndActiveTrueOrderByCreatedAtDesc(groupId).stream()
            .map(
                template ->
                    summary(
                        template,
                        assignmentsByTemplate.getOrDefault(template.getId(), List.of())))
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
    Map<Long, TaskAttempt> latestAttempts = latestAttemptsByAssignment(taskAssignments);
    Map<Long, TaskPhoto> photosByAttempt = photosByAttempt(latestAttempts);
    TaskAssignment primaryAssignment = taskAssignments.getFirst();
    return new TaskDetail(
        taskId,
        template.getGroup().getId(),
        template.getTitle(),
        template.getSourceMessage(),
        template.getCreator().getId(),
        template.getCreator().getNickname(),
        primaryAssignment.getAssignee() == null
            ? null
            : primaryAssignment.getAssignee().getId(),
        primaryAssignment.getAssignee() == null
            ? null
            : primaryAssignment.getAssignee().getNickname(),
        toOffsetDateTime(primaryAssignment.getDueAt()),
        summary.status(),
        summary.progress(),
        template.isNotifyOnCompletion(),
        toOffsetDateTime(template.getCreatedAt()),
        taskAssignments.stream()
            .map(assignment -> checklist(assignment, latestAttempts, photosByAttempt))
            .toList());
  }

  private Map<Long, TaskAttempt> latestAttemptsByAssignment(
      List<TaskAssignment> taskAssignments) {
    Map<Long, TaskAttempt> latestAttempts = new HashMap<>();
    attempts
        .findAllByAssignmentIdInOrderByAssignmentIdAscAttemptNumberDesc(
            taskAssignments.stream().map(TaskAssignment::getId).toList())
        .forEach(
            attempt -> latestAttempts.putIfAbsent(attempt.getAssignment().getId(), attempt));
    return latestAttempts;
  }

  private Map<Long, TaskPhoto> photosByAttempt(Map<Long, TaskAttempt> latestAttempts) {
    if (latestAttempts.isEmpty()) return Map.of();

    return photos.findAllByAttemptIdIn(
            latestAttempts.values().stream().map(TaskAttempt::getId).toList())
        .stream()
        .collect(Collectors.toMap(photo -> photo.getAttempt().getId(), photo -> photo));
  }

  private long templateId(TaskAssignment assignment) {
    return assignment.getSchedule().getTaskTemplate().getId();
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
            .anyMatch(a -> a.getTaskItemTemplate().getCompletionType() == CompletionType.PHOTO));
  }

  private Checklist checklist(
      TaskAssignment assignment,
      Map<Long, TaskAttempt> latestAttempts,
      Map<Long, TaskPhoto> photosByAttempt) {
    TaskItemTemplate item = assignment.getTaskItemTemplate();
    TaskAttempt attempt = latestAttempts.get(assignment.getId());
    TaskPhoto photo = attempt == null ? null : photosByAttempt.get(attempt.getId());
    return new Checklist(
        assignment.getId(),
        item.getSequence(),
        item.getTitle(),
        item.getInstruction(),
        item.getCompletionType(),
        item.getVerificationRule(),
        item.isEnabled(),
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
    private TaskSummary(Long taskId, String title, Member worker,
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
      boolean notifyOnCompletion,
      OffsetDateTime createdAt,
      List<Checklist> checklists) {}

  public record Checklist(
      Long checklistId,
      int sequence,
      String title,
      String instruction,
      CompletionType completionType,
      String rule,
      boolean enabled,
      String referencePhotoUrl,
      boolean performed,
      OffsetDateTime performedAt,
      String submittedPhotoUrl) {}
}
