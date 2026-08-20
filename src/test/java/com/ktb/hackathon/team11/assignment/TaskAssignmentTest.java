package com.ktb.hackathon.team11.assignment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.schedule.TaskSchedule;
import com.ktb.hackathon.team11.task.TaskItemTemplate;
import java.time.*;
import org.junit.jupiter.api.Test;

class TaskAssignmentTest {
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 10, 0);

  @Test
  void photoCanBeSubmittedOnlyWhilePendingOrRetakeRequired() {
    TaskAssignment assignment = assignment();

    assertThatCode(() -> assignment.requirePhotoSubmissionAvailable(NOW)).doesNotThrowAnyException();

    assignment.retake();

    assertThatCode(() -> assignment.requirePhotoSubmissionAvailable(NOW)).doesNotThrowAnyException();
  }

  @Test
  void blocksPhotoSubmissionWhileVerifyingDelayedOrCompleted() {
    TaskAssignment verifying = assignment();
    verifying.verifying();
    assertError(ErrorCode.TASK_NOT_AVAILABLE, verifying);

    TaskAssignment delayed = assignment();
    delayed.delayed();
    assertError(ErrorCode.TASK_NOT_AVAILABLE, delayed);

    TaskAssignment completed = assignment();
    completed.pass(NOW);
    assertError(ErrorCode.ASSIGNMENT_ALREADY_COMPLETED, completed);
  }

  @Test
  void blocksPhotoSubmissionOutsideAvailableWindow() {
    TaskAssignment assignment = assignment();

    assertThatThrownBy(
            () -> assignment.requirePhotoSubmissionAvailable(NOW.minusHours(2)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.TASK_NOT_AVAILABLE);
  }

  private TaskAssignment assignment() {
    TaskSchedule schedule = mock(TaskSchedule.class);
    when(schedule.getAssignee()).thenReturn(null);
    return new TaskAssignment(
        schedule,
        mock(TaskItemTemplate.class),
        NOW.toLocalDate(),
        NOW.minusHours(1),
        NOW.plusHours(1));
  }

  private void assertError(ErrorCode expected, TaskAssignment assignment) {
    assertThatThrownBy(() -> assignment.requirePhotoSubmissionAvailable(NOW))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(expected);
  }
}
