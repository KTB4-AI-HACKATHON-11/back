package com.ktb.hackathon.team11.attempt;

import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "task_attempts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "attempt_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAttempt extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assignment_id")
  private TaskAssignment assignment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Member submitter;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AttemptStatus status = AttemptStatus.VERIFYING;

  @Column(length = 500)
  private String reason;

  @Column(length = 500)
  private String fixMessage;

  @Column(nullable = false)
  private LocalDateTime submittedAt;

  public TaskAttempt(TaskAssignment a, Member m, int n, LocalDateTime at) {
    assignment = a;
    submitter = m;
    attemptNumber = n;
    submittedAt = at;
  }

  public void pass(String r) {
    status = AttemptStatus.PASS;
    reason = r;
    fixMessage = null;
  }

  public void retake(String r, String f) {
    status = AttemptStatus.RETAKE;
    reason = r;
    fixMessage = f;
  }

  public void delayed() {
    status = AttemptStatus.DELAYED;
    reason = "AI 검사 지연 중입니다.";
    fixMessage = null;
  }

  public void verifying() {
    status = AttemptStatus.VERIFYING;
    reason = null;
    fixMessage = null;
  }
}
