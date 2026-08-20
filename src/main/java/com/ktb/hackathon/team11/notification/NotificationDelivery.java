package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "notification_deliveries",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notification_delivery_target",
            columnNames = {"outbox_id", "subscription_id"}),
    indexes =
        @Index(
            name = "idx_notification_delivery_pending",
            columnList = "status,next_attempt_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "outbox_id", nullable = false)
  private NotificationOutbox outbox;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private PushSubscriptionRecord subscription;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationDeliveryStatus status = NotificationDeliveryStatus.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private LocalDateTime nextAttemptAt;

  private LocalDateTime sentAt;

  @Column(length = 1000)
  private String lastError;

  public NotificationDelivery(
      NotificationOutbox outbox, PushSubscriptionRecord subscription, LocalDateTime now) {
    this.outbox = outbox;
    this.subscription = subscription;
    nextAttemptAt = now;
  }

  public boolean isDue(LocalDateTime now) {
    return status == NotificationDeliveryStatus.PENDING && !nextAttemptAt.isAfter(now);
  }

  public void sent(LocalDateTime now) {
    status = NotificationDeliveryStatus.SENT;
    sentAt = now;
    lastError = null;
  }

  public void retry(LocalDateTime now, String error) {
    attempts++;
    nextAttemptAt = now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
    lastError = abbreviate(error);
  }

  public void cancel(String error) {
    status = NotificationDeliveryStatus.CANCELLED;
    lastError = abbreviate(error);
  }

  public void reopen(LocalDateTime now) {
    status = NotificationDeliveryStatus.PENDING;
    attempts = 0;
    nextAttemptAt = now;
    sentAt = null;
    lastError = null;
  }

  private String abbreviate(String value) {
    return value == null ? null : value.substring(0, Math.min(value.length(), 1000));
  }
}
