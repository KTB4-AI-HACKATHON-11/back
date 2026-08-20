package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.assignment.TaskAssignmentRepository;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.schedule.RecurrenceType;
import com.ktb.hackathon.team11.schedule.TaskSchedule;
import com.ktb.hackathon.team11.schedule.TaskScheduleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentTaskMutationService {
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

  private final TaskTemplateRepository templates;
  private final TaskAssignmentRepository assignments;
  private final TaskScheduleRepository schedules;
  private final GroupService groups;
  private final Clock clock;

  @Transactional
  public DeactivatedTask deactivate(long groupId, long managerId, long taskId) {
    groups.requireManager(groupId, managerId);
    TaskTemplate template =
        templates
            .findByIdAndGroupId(taskId, groupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    template.deactivate();
    return new DeactivatedTask(template.getId(), template.getTitle());
  }

  @Transactional
  public UpdatedTask update(
      long groupId,
      long managerId,
      long taskId,
      String title,
      String sourceMessage,
      Long workerId,
      OffsetDateTime dueAt,
      Boolean notifyOnCompletion,
      Boolean active) {
    groups.requireManager(groupId, managerId);
    validateText(title, 80);
    validateText(sourceMessage, 2_000);
    TaskTemplate template =
        templates
            .findByIdAndGroupId(taskId, groupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    template.updateByAgent(title, sourceMessage, notifyOnCompletion, active);

    List<TaskAssignment> taskAssignments =
        assignments.findAllByScheduleTaskTemplateId(taskId);
    if (taskAssignments.isEmpty()) throw new BusinessException(ErrorCode.TASK_NOT_FOUND);

    TaskAssignment first = taskAssignments.getFirst();
    Member worker = first.getAssignee();
    LocalDateTime updatedDueAt = first.getDueAt();
    if (workerId != null || dueAt != null) {
      List<TaskSchedule> taskSchedules = schedules.findAllByTaskTemplateId(taskId);
      if (taskSchedules.size() != 1
          || taskSchedules.getFirst().getRecurrenceType() != RecurrenceType.ONCE) {
        throw new BusinessException(ErrorCode.RECURRING_TASK_SCHEDULE_UPDATE_UNSUPPORTED);
      }
      if (taskAssignments.stream()
          .anyMatch(assignment -> assignment.getStatus() != AssignmentStatus.PENDING)) {
        throw new BusinessException(ErrorCode.TASK_ALREADY_STARTED);
      }
      if (workerId != null) worker = groups.requireWorker(groupId, workerId).getMember();
      if (worker == null) throw new BusinessException(ErrorCode.WORKER_NOT_IN_GROUP);
      if (dueAt != null) updatedDueAt = dueAt.atZoneSameInstant(SERVICE_ZONE).toLocalDateTime();
      if (!updatedDueAt.isAfter(LocalDateTime.now(clock)))
        throw new BusinessException(ErrorCode.INVALID_DUE_AT);

      Member finalWorker = worker;
      LocalDateTime finalDueAt = updatedDueAt;
      taskAssignments.forEach(assignment -> assignment.updateSchedule(finalWorker, finalDueAt));
      TaskSchedule schedule = taskSchedules.getFirst();
      schedule.updateAssignee(worker, updatedDueAt);
    }

    return new UpdatedTask(
        taskId,
        template.getTitle(),
        template.isActive(),
        worker == null ? null : worker.getId(),
        worker == null ? null : worker.getNickname(),
        updatedDueAt.atZone(SERVICE_ZONE).toOffsetDateTime());
  }

  private void validateText(String value, int maxLength) {
    if (value != null && (value.isBlank() || value.strip().length() > maxLength))
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
  }

  public record UpdatedTask(
      long taskId,
      String title,
      boolean active,
      Long workerId,
      String workerNickname,
      OffsetDateTime dueAt) {}

  public record DeactivatedTask(long taskId, String title) {}
}
