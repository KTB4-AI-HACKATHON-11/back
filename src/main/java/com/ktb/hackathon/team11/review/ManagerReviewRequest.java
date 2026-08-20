package com.ktb.hackathon.team11.review;

import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.attempt.TaskAttempt;
import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "manager_review_requests",
    indexes =
        @Index(
            name = "idx_manager_review_group_status",
            columnList = "group_id,status,requested_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagerReviewRequest extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assignment_id", nullable = false)
  private TaskAssignment assignment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "attempt_id", nullable = false)
  private TaskAttempt attempt;

  @Column(name = "group_id", nullable = false)
  private Long groupId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requester_id", nullable = false)
  private Member requester;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewer_id")
  private Member reviewer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ManagerReviewStatus status = ManagerReviewStatus.PENDING;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  private LocalDateTime resolvedAt;

  @Column(length = 500)
  private String message;

  public ManagerReviewRequest(
      TaskAssignment assignment,
      TaskAttempt attempt,
      Member requester,
      LocalDateTime requestedAt) {
    this.assignment = assignment;
    this.attempt = attempt;
    groupId = assignment.getSchedule().getTaskTemplate().getGroup().getId();
    this.requester = requester;
    this.requestedAt = requestedAt;
  }

  public void resolve(
      ManagerReviewDecision decision, Member reviewer, String message, LocalDateTime resolvedAt) {
    if (status != ManagerReviewStatus.PENDING)
      throw new BusinessException(ErrorCode.MANAGER_REVIEW_ALREADY_RESOLVED);
    status =
        decision == ManagerReviewDecision.APPROVE
            ? ManagerReviewStatus.APPROVED
            : ManagerReviewStatus.RETAKE_REQUESTED;
    this.reviewer = reviewer;
    this.message = message == null || message.isBlank() ? null : message.strip();
    this.resolvedAt = resolvedAt;
  }
}
