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
import com.ktb.hackathon.team11.member.MemberRole;
import com.ktb.hackathon.team11.storage.FileStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
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
    Map<TaskRunId, List<TaskAssignment>> assignmentsByRun =
        assignments.findAllByScheduleTaskTemplateGroupId(groupId).stream()
            .collect(Collectors.groupingBy(TaskRunId::from));
    List<TaskSummary> all =
        assignmentsByRun.entrySet().stream()
            .filter(entry -> entry.getValue().getFirst().getSchedule().getTaskTemplate().isActive())
            .map(
                entry ->
                    summary(
                        entry.getKey(),
                        entry.getValue().getFirst().getSchedule().getTaskTemplate(),
                        entry.getValue()))
            .filter(summary -> filter == null || summary.status() == filter)
            .sorted(
                Comparator.comparing(TaskSummary::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed())
            .toList();

    int from = Math.min(offset, all.size());
    int to = Math.min(from + limit, all.size());
    return new TaskListResponse(all.size(), all.subList(from, to));
  }

  public TaskDetail detail(long taskId, long requesterId) {
    TaskTemplate template =
        templates.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    boolean canManage =
        groups.requireMember(template.getGroup().getId(), requesterId).getGroupRole()
            == MemberRole.MANAGER;
    Map<TaskRunId, List<TaskAssignment>> runs =
        taskAssignments(taskId).stream().collect(Collectors.groupingBy(TaskRunId::from));
    var latestRun =
        runs.entrySet().stream()
            .max(
                Comparator.comparing((Map.Entry<TaskRunId, List<TaskAssignment>> entry) -> entry.getKey().scheduledDate())
                    .thenComparing(entry -> entry.getKey().scheduleId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    return detail(template, latestRun.getKey(), latestRun.getValue(), canManage);
  }

  public TaskDetail detailRun(String runId, long requesterId) {
    TaskRunId key = TaskRunId.parse(runId);
    List<TaskAssignment> runAssignments =
        assignments.findAllByScheduleIdAndScheduledDate(key.scheduleId(), key.scheduledDate());
    if (runAssignments.isEmpty()) throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    TaskTemplate template = runAssignments.getFirst().getSchedule().getTaskTemplate();
    boolean canManage =
        groups.requireMember(template.getGroup().getId(), requesterId).getGroupRole()
            == MemberRole.MANAGER;
    return detail(template, key, runAssignments, canManage);
  }

  /**
   * 에이전트가 관련 실행 회차를 판단할 때 필요한 최소 상세만 반환한다.
   * 사진 URL과 제출 이력은 만들지 않아 컨텍스트 조회가 S3 presign 작업을 유발하지 않는다.
   */
  public List<AgentTaskDetail> agentDetails(
      long groupId, long requesterId, List<String> runIds) {
    groups.requireMember(groupId, requesterId);
    if (runIds == null || runIds.isEmpty()) return List.of();
    if (runIds.size() > 5) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

    return runIds.stream()
        .distinct()
        .map(TaskRunId::parse)
        .map(
            runId -> {
              List<TaskAssignment> runAssignments =
                  assignments.findAllByScheduleIdAndScheduledDate(
                      runId.scheduleId(), runId.scheduledDate());
              if (runAssignments.isEmpty())
                throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
              TaskTemplate template =
                  runAssignments.getFirst().getSchedule().getTaskTemplate();
              if (!template.getGroup().getId().equals(groupId))
                throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
              List<TaskAssignment> ordered =
                  runAssignments.stream()
                      .sorted(
                          Comparator.comparing(
                              item -> item.getTaskItemTemplate().getSequence()))
                      .toList();
              TaskSummary summary = summary(runId, template, ordered);
              TaskAssignment primary = ordered.getFirst();
              return new AgentTaskDetail(
                  template.getId(),
                  runId.value(),
                  template.getTitle(),
                  template.getSourceMessage(),
                  primary.getAssignee() == null ? null : primary.getAssignee().getId(),
                  primary.getAssignee() == null
                      ? null
                      : primary.getAssignee().getNickname(),
                  toOffsetDateTime(primary.getDueAt()),
                  summary.status(),
                  template.isNotifyOnCompletion(),
                  ordered.stream()
                      .map(
                          assignment ->
                              new AgentChecklistDetail(
                                  assignment.getId(),
                                  assignment.getTaskItemTemplate().getTitle(),
                                  assignment.getTaskItemTemplate().getInstruction(),
                                  assignment.getTaskItemTemplate().getCompletionType(),
                                  assignment.getTaskItemTemplate().getVerificationRule(),
                                  performed(assignment)))
                      .toList());
            })
        .toList();
  }

  private TaskDetail detail(
      TaskTemplate template,
      TaskRunId runId,
      List<TaskAssignment> runAssignments,
      boolean canManage) {
    List<TaskAssignment> taskAssignments =
        runAssignments.stream()
            .sorted(Comparator.comparing(a -> a.getTaskItemTemplate().getSequence()))
            .toList();
    TaskSummary summary = summary(runId, template, taskAssignments);
    Map<Long, TaskAttempt> latestAttempts = latestAttemptsByAssignment(taskAssignments);
    Map<Long, TaskPhoto> photosByAttempt = photosByAttempt(latestAttempts);
    TaskAssignment primaryAssignment = taskAssignments.getFirst();
    return new TaskDetail(
        template.getId(),
        runId.value(),
        runId.scheduleId(),
        runId.scheduledDate(),
        template.getGroup().getId(),
        template.getGroup().getName(),
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
        canManage,
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

  private TaskSummary summary(
      TaskRunId runId, TaskTemplate template, List<TaskAssignment> taskAssignments) {
    int itemCount = taskAssignments.size();
    int completed = (int) taskAssignments.stream().filter(this::performed).count();
    return new TaskSummary(
        template.getId(),
        runId.value(),
        template.getTitle(),
        taskAssignments.isEmpty() ? null : taskAssignments.get(0).getAssignee(),
        taskAssignments.isEmpty() ? null : toOffsetDateTime(taskAssignments.get(0).getDueAt()),
        status(taskAssignments),
        itemCount,
        completed,
        progress(completed, itemCount),
        template.isNotifyOnCompletion(),
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
        assignment.getId(),
        item.getSequence(),
        item.getTitle(),
        item.getInstruction(),
        item.getCompletionType(),
        item.getVerificationRule(),
        item.isEnabled(),
        item.hasReferenceImage() ? storage.createReadUrl(item.getReferenceImageKey(), Duration.ofMinutes(urlMinutes)) : null,
        assignment.getStatus(),
        attempt == null ? null : attempt.getId(),
        attempt == null ? null : attempt.getStatus(),
        attempt == null ? 0 : attempt.getAttemptNumber(),
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

  public record AgentTaskDetail(
      Long taskId,
      String runId,
      String title,
      String sourceMessage,
      Long workerId,
      String workerNickname,
      OffsetDateTime dueAt,
      TaskStatus status,
      boolean notifyOnCompletion,
      List<AgentChecklistDetail> checklists) {}

  public record AgentChecklistDetail(
      Long checklistId,
      String title,
      String instruction,
      CompletionType completionType,
      String rule,
      boolean performed) {}

  public record TaskSummary(
      Long taskId,
      String runId,
      String title,
      Long workerId,
      String workerNickname,
      OffsetDateTime dueAt,
      TaskStatus status,
      int itemCount,
      int completedItemCount,
      int progress,
      boolean notifyOnCompletion,
      boolean hasPhotoVerification) {
    private TaskSummary(Long taskId, String runId, String title, Member worker,
        OffsetDateTime dueAt, TaskStatus status, int itemCount, int completedItemCount,
        int progress, boolean notifyOnCompletion, boolean hasPhotoVerification) {
      this(taskId, runId, title, worker == null ? null : worker.getId(), worker == null ? null : worker.getNickname(),
          dueAt, status, itemCount, completedItemCount, progress, notifyOnCompletion, hasPhotoVerification);
    }
  }

  public record TaskDetail(
      Long taskId,
      String runId,
      Long scheduleId,
      LocalDate scheduledDate,
      Long groupId,
      String groupName,
      String title,
      String message,
      Long managerId,
      String managerNickname,
      Long workerId,
      String workerNickname,
      OffsetDateTime dueAt,
      TaskStatus status,
      int progress,
      boolean canManage,
      boolean notifyOnCompletion,
      OffsetDateTime createdAt,
      List<Checklist> checklists) {}

  public record Checklist(
      Long checklistId,
      Long assignmentId,
      int sequence,
      String title,
      String instruction,
      CompletionType completionType,
      String rule,
      boolean enabled,
      String referencePhotoUrl,
      AssignmentStatus assignmentStatus,
      Long latestAttemptId,
      com.ktb.hackathon.team11.attempt.AttemptStatus latestAttemptStatus,
      int attemptNumber,
      boolean performed,
      OffsetDateTime performedAt,
      String submittedPhotoUrl) {}
}
