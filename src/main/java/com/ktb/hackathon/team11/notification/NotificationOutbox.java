package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.task.TaskTemplate;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Table(
    name = "notification_outbox",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notification_outbox_task_run",
            columnNames = {"schedule_id", "scheduled_date", "recipient_id", "type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "schedule_id", nullable = false)
  private Long scheduleId;

  @Column(name = "scheduled_date", nullable = false)
  private LocalDate scheduledDate;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_template_id", nullable = false)
  private TaskTemplate taskTemplate;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  private Member recipient;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private NotificationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationOutboxStatus status = NotificationOutboxStatus.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private LocalDateTime nextAttemptAt;

  private LocalDateTime sentAt;

  @Column(length = 1000)
  private String lastError;

  public NotificationOutbox(
      Long scheduleId,
      LocalDate scheduledDate,
      TaskTemplate taskTemplate,
      Member recipient,
      NotificationType type,
      LocalDateTime now) {
    this.scheduleId = scheduleId;
    this.scheduledDate = scheduledDate;
    this.taskTemplate = taskTemplate;
    this.recipient = recipient;
    this.type = type;
    nextAttemptAt = now;
  }

  public void sent(LocalDateTime now) {
    status = NotificationOutboxStatus.SENT;
    sentAt = now;
    lastError = null;
  }

  public void retry(LocalDateTime now, String error) {
    attempts++;
    nextAttemptAt = now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
    lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
  }

  public void cancel(String reason) {
    status = NotificationOutboxStatus.CANCELLED;
    lastError = reason;
  }

  public void reopen(LocalDateTime now) {
    status = NotificationOutboxStatus.PENDING;
    attempts = 0;
    nextAttemptAt = now;
    sentAt = null;
    lastError = null;
  }
}
