package com.ktb.hackathon.team11.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AgentNotificationDispatcher {
  private final AgentNotificationOutboxRepository outboxes;
  private final AgentNotificationDeliveryRepository deliveries;
  private final PushSubscriptionRepository subscriptions;
  private final WebPushSender sender;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${web-push.dispatch-delay-ms:1000}")
  @Transactional
  public void dispatch() {
    if (!sender.isEnabled()) return;
    LocalDateTime now = LocalDateTime.now(clock);
    outboxes
        .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            NotificationOutboxStatus.PENDING, now)
        .forEach(outbox -> dispatch(outbox, now));
  }

  private void dispatch(AgentNotificationOutbox outbox, LocalDateTime now) {
    List<PushSubscriptionRecord> targets =
        subscriptions.findAllByMemberIdAndActiveTrue(outbox.getRecipient().getId());
    if (targets.isEmpty()) {
      outbox.retry(now, "활성화된 브라우저 알림 구독을 기다리는 중입니다.");
      return;
    }

    targets.forEach(
        target ->
            deliveries
                .findByOutboxIdAndSubscriptionId(outbox.getId(), target.getId())
                .orElseGet(
                    () -> deliveries.save(new AgentNotificationDelivery(outbox, target, now))));

    String payload = payload(outbox);
    String lastError = "";
    for (AgentNotificationDelivery delivery : deliveries.findAllByOutboxId(outbox.getId())) {
      if (!delivery.isDue(now)) continue;
      PushSubscriptionRecord target = delivery.getSubscription();
      if (!target.isActive()) {
        delivery.cancel("비활성화된 브라우저 구독입니다.");
        continue;
      }
      WebPushSender.DeliveryResult result = sender.send(target, payload);
      if (result.successful()) {
        target.succeeded(now);
        delivery.sent(now);
      } else {
        lastError = diagnostic(result);
        if (result.expired()) {
          target.deactivate(lastError);
          delivery.cancel(lastError);
        } else {
          target.failed(lastError);
          delivery.retry(now, lastError);
        }
      }
    }

    List<AgentNotificationDelivery> results = deliveries.findAllByOutboxId(outbox.getId());
    if (results.stream().anyMatch(item -> item.getStatus() == NotificationDeliveryStatus.PENDING)) {
      outbox.retry(
          now,
          lastError.isBlank() ? "일부 브라우저 전달 재시도 대기 중입니다." : lastError);
    } else if (results.stream().anyMatch(item -> item.getStatus() == NotificationDeliveryStatus.SENT)) {
      outbox.sent(now);
    } else {
      outbox.cancel("전달 가능한 브라우저 구독이 없습니다.");
    }
  }

  private String payload(AgentNotificationOutbox outbox) {
    try {
      return objectMapper.writeValueAsString(
          new Payload(outbox.getTitle(), outbox.getBody(), outbox.getUrl(), outbox.getTag()));
    } catch (JacksonException exception) {
      throw new IllegalStateException("에이전트 알림 payload 직렬화에 실패했습니다.", exception);
    }
  }

  private String diagnostic(WebPushSender.DeliveryResult result) {
    String requestId = result.requestId().isBlank() ? "" : " requestId=" + result.requestId();
    return "status=" + result.statusCode() + " reason=" + result.reason() + requestId;
  }

  private record Payload(String title, String body, String url, String tag) {}
}
