package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "agent_notification_outbox",
    indexes =
        @Index(
            name = "idx_agent_notification_outbox_pending",
            columnList = "status,next_attempt_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentNotificationOutbox extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  private Member recipient;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, length = 500)
  private String body;

  @Column(nullable = false, length = 500)
  private String url;

  @Column(nullable = false, length = 160)
  private String tag;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationOutboxStatus status = NotificationOutboxStatus.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private LocalDateTime nextAttemptAt;

  private LocalDateTime sentAt;

  @Column(length = 1000)
  private String lastError;

  public AgentNotificationOutbox(
      Member recipient,
      String title,
      String body,
      String url,
      String tag,
      LocalDateTime now) {
    this.recipient = recipient;
    this.title = title;
    this.body = body;
    this.url = url;
    this.tag = tag;
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
    lastError = abbreviate(error);
  }

  public void cancel(String error) {
    status = NotificationOutboxStatus.CANCELLED;
    lastError = abbreviate(error);
  }

  private String abbreviate(String value) {
    return value == null ? null : value.substring(0, Math.min(value.length(), 1000));
  }
}
