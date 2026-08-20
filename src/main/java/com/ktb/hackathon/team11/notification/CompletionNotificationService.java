package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.task.TaskTemplate;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompletionNotificationService {
  private final TaskAssignmentRepository assignments;
  private final NotificationOutboxRepository outboxes;
  private final Clock clock;

  @Transactional
  public void afterStateChange(TaskAssignment assignment) {
    if (assignment.getStatus() != AssignmentStatus.COMPLETED) return;
    TaskTemplate template = assignment.getSchedule().getTaskTemplate();
    if (!template.isNotifyOnCompletion()) return;

    List<TaskAssignment> runAssignments =
        assignments.findAllByScheduleIdAndScheduledDate(
            assignment.getSchedule().getId(), assignment.getScheduledDate());
    if (runAssignments.isEmpty()
        || runAssignments.stream().anyMatch(item -> item.getStatus() != AssignmentStatus.COMPLETED))
      return;

    Long recipientId = template.getCreator().getId();
    if (outboxes.existsByScheduleIdAndScheduledDateAndRecipientIdAndType(
        assignment.getSchedule().getId(),
        assignment.getScheduledDate(),
        recipientId,
        NotificationType.TASK_COMPLETED)) return;

    outboxes.save(
        new NotificationOutbox(
            assignment.getSchedule().getId(),
            assignment.getScheduledDate(),
            template,
            template.getCreator(),
            NotificationType.TASK_COMPLETED,
            LocalDateTime.now(clock)));
  }
}
