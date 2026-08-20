package com.ktb.hackathon.team11.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.schedule.TaskSchedule;
import com.ktb.hackathon.team11.task.TaskTemplate;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompletionNotificationServiceTest {
  @Mock private TaskAssignmentRepository assignments;
  @Mock private NotificationOutboxRepository outboxes;
  @Mock private TaskAssignment changed;
  @Mock private TaskAssignment sibling;
  @Mock private TaskSchedule schedule;
  @Mock private TaskTemplate template;
  @Mock private Member creator;
  private CompletionNotificationService service;
  private final LocalDate scheduledDate = LocalDate.of(2026, 8, 21);

  @BeforeEach
  void setUp() {
    service =
        new CompletionNotificationService(
            assignments,
            outboxes,
            Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC));
    lenient().when(changed.getStatus()).thenReturn(AssignmentStatus.COMPLETED);
    lenient().when(changed.getSchedule()).thenReturn(schedule);
    lenient().when(changed.getScheduledDate()).thenReturn(scheduledDate);
    lenient().when(schedule.getId()).thenReturn(10L);
    lenient().when(schedule.getTaskTemplate()).thenReturn(template);
    lenient().when(template.isNotifyOnCompletion()).thenReturn(true);
    lenient().when(template.getCreator()).thenReturn(creator);
    lenient().when(creator.getId()).thenReturn(3L);
  }

  @Test
  void queuesOneNotificationWhenEveryAssignmentInRunIsCompleted() {
    when(sibling.getStatus()).thenReturn(AssignmentStatus.COMPLETED);
    when(assignments.findAllByScheduleIdAndScheduledDate(10L, scheduledDate))
        .thenReturn(List.of(changed, sibling));
    when(outboxes.existsByScheduleIdAndScheduledDateAndRecipientIdAndType(
            10L, scheduledDate, 3L, NotificationType.TASK_COMPLETED))
        .thenReturn(false);

    service.afterStateChange(changed);

    verify(outboxes).save(any(NotificationOutbox.class));
  }

  @Test
  void doesNotQueueBeforeEveryAssignmentIsCompleted() {
    when(sibling.getStatus()).thenReturn(AssignmentStatus.PENDING);
    when(assignments.findAllByScheduleIdAndScheduledDate(10L, scheduledDate))
        .thenReturn(List.of(changed, sibling));

    service.afterStateChange(changed);

    verify(outboxes, never()).save(any());
  }

  @Test
  void doesNotQueueDuplicateForSameScheduledRun() {
    when(assignments.findAllByScheduleIdAndScheduledDate(10L, scheduledDate))
        .thenReturn(List.of(changed));
    when(outboxes.existsByScheduleIdAndScheduledDateAndRecipientIdAndType(
            10L, scheduledDate, 3L, NotificationType.TASK_COMPLETED))
        .thenReturn(true);

    service.afterStateChange(changed);

    verify(outboxes, never()).save(any());
  }
}
