package com.ktb.hackathon.team11.agent;

import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.assignment.TaskAssignmentRepository;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.notification.CompletionNotificationService;
import com.ktb.hackathon.team11.task.TaskRunId;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentChecklistActionService {
  private final TaskAssignmentRepository assignments;
  private final GroupService groups;
  private final CompletionNotificationService completionNotifications;
  private final Clock clock;

  @Transactional
  public CompletedChecklist complete(
      long groupId,
      long managerId,
      long taskId,
      String runId,
      long checklistId) {
    groups.requireManager(groupId, managerId);
    TaskAssignment assignment =
        assignments
            .findByIdForUpdate(checklistId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
    if (!assignment.getSchedule().getTaskTemplate().getGroup().getId().equals(groupId)
        || !assignment.getSchedule().getTaskTemplate().getId().equals(taskId)
        || !TaskRunId.from(assignment).value().equals(runId))
      throw new BusinessException(ErrorCode.TASK_NOT_FOUND);

    assignment.completeCheck(LocalDateTime.now(clock));
    completionNotifications.afterStateChange(assignment);
    return new CompletedChecklist(
        taskId,
        runId,
        checklistId,
        assignment.getTaskItemTemplate().getTitle());
  }

  public record CompletedChecklist(
      long taskId, String runId, long checklistId, String title) {}
}
