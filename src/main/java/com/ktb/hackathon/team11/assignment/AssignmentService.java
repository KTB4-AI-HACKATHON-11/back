package com.ktb.hackathon.team11.assignment;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.notification.CompletionNotificationService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {
  private final TaskAssignmentRepository repository;
  private final MemberService members;
  private final GroupService groups;
  private final CompletionNotificationService completionNotifications;
  private final Clock clock;

  public TaskAssignment require(long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
  }

  public TaskAssignment requireForUpdate(long id) {
    return repository
        .findByIdForUpdate(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
  }

  public List<TaskAssignment> worker(long workerId, LocalDate date) {
    members.requireRole(workerId, MemberRole.WORKER);
    List<TaskAssignment> result =
        new ArrayList<>(repository.findAllByScheduledDateAndAssigneeId(date, workerId));
    for (TaskAssignment a : repository.findAllByScheduledDateAndAssigneeIsNull(date))
      try {
        groups.requireMember(a.getSchedule().getTaskTemplate().getGroup().getId(), workerId);
        result.add(a);
      } catch (BusinessException ignored) {
      }
    return result;
  }

  public List<TaskAssignment> group(long groupId, long managerId, LocalDate date) {
    groups.requireManager(groupId, managerId);
    return repository.findAllByScheduleTaskTemplateGroupIdAndScheduledDate(groupId, date);
  }

  @Transactional
  public TaskAssignment check(long id, long workerId) {
    TaskAssignment a = require(id);
    requireGroupWorker(a.getSchedule().getTaskTemplate().getGroup().getId(), workerId);
    if (a.getAssignee() != null && !a.getAssignee().getId().equals(workerId))
      throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
    a.completeCheck(LocalDateTime.now(clock));
    completionNotifications.afterStateChange(a);
    return a;
  }

  @Transactional
  public TaskAssignment updatePerformed(
      long taskId, long checklistId, long workerId, boolean performed) {
    TaskAssignment assignment =
        repository
            .findByIdAndScheduleTaskTemplateId(checklistId, taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    long groupId = assignment.getSchedule().getTaskTemplate().getGroup().getId();
    requireGroupWorker(groupId, workerId);
    if (assignment.getAssignee() != null && !assignment.getAssignee().getId().equals(workerId))
      throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
    if (performed) {
      assignment.completeCheck(LocalDateTime.now(clock));
      completionNotifications.afterStateChange(assignment);
    } else assignment.uncompleteCheck(LocalDateTime.now(clock));
    return assignment;
  }

  private void requireGroupWorker(long groupId, long workerId) {
    if (groups.requireMember(groupId, workerId).getGroupRole() != MemberRole.WORKER)
      throw new BusinessException(ErrorCode.WORKER_NOT_IN_GROUP);
  }
}
