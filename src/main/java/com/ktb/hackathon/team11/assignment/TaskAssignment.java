package com.ktb.hackathon.team11.assignment;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.schedule.TaskSchedule;
import com.ktb.hackathon.team11.task.TaskItemTemplate;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Table(
    name = "task_assignments",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"schedule_id", "task_item_template_id", "scheduled_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAssignment extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "schedule_id")
  private TaskSchedule schedule;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_item_template_id")
  private TaskItemTemplate taskItemTemplate;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member assignee;

  @Column(name = "scheduled_date", nullable = false)
  private LocalDate scheduledDate;

  @Column(nullable = false)
  private LocalDateTime availableFrom;

  @Column(nullable = false)
  private LocalDateTime dueAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AssignmentStatus status = AssignmentStatus.PENDING;

  private LocalDateTime completedAt;
  @Version private long version;

  public TaskAssignment(
      TaskSchedule s, TaskItemTemplate i, LocalDate d, LocalDateTime from, LocalDateTime due) {
    schedule = s;
    taskItemTemplate = i;
    assignee = s.getAssignee();
    scheduledDate = d;
    availableFrom = from;
    dueAt = due;
  }

  public void requireAvailable(LocalDateTime now) {
    if (now.isBefore(availableFrom) || now.isAfter(dueAt))
      throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
    if (status == AssignmentStatus.COMPLETED)
      throw new BusinessException(ErrorCode.ASSIGNMENT_ALREADY_COMPLETED);
    if (status == AssignmentStatus.EXPIRED)
      throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
  }

  public void requirePhotoSubmissionAvailable(LocalDateTime now) {
    if (now.isBefore(availableFrom) || now.isAfter(dueAt))
      throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
    if (status == AssignmentStatus.COMPLETED)
      throw new BusinessException(ErrorCode.ASSIGNMENT_ALREADY_COMPLETED);
    if (status != AssignmentStatus.PENDING && status != AssignmentStatus.RETAKE_REQUIRED)
      throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
  }

  public void completeCheck(LocalDateTime now) {
    requireAvailable(now);
    if (taskItemTemplate.getCompletionType() != CompletionType.CHECK)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    status = AssignmentStatus.COMPLETED;
    completedAt = now;
  }

  public void uncompleteCheck(LocalDateTime now) {
    if (taskItemTemplate.getCompletionType() != CompletionType.CHECK)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    if (now.isBefore(availableFrom) || now.isAfter(dueAt))
      throw new BusinessException(ErrorCode.TASK_NOT_AVAILABLE);
    status = AssignmentStatus.PENDING;
    completedAt = null;
  }

  public void verifying() {
    status = AssignmentStatus.VERIFYING;
  }

  public void pass(LocalDateTime now) {
    status = AssignmentStatus.COMPLETED;
    completedAt = now;
  }

  public void retake() {
    status = AssignmentStatus.RETAKE_REQUIRED;
  }

  public void delayed() {
    status = AssignmentStatus.VERIFICATION_DELAYED;
  }

  public void updateSchedule(Member worker, LocalDateTime newDueAt) {
    if (!newDueAt.isAfter(availableFrom))
      throw new BusinessException(ErrorCode.INVALID_DUE_AT);
    assignee = worker;
    dueAt = newDueAt;
  }
}
