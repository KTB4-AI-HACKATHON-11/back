package com.ktb.hackathon.team11.notification;

import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class NotificationDispatcher {
  private final NotificationOutboxRepository outboxes;
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

  private void dispatch(NotificationOutbox outbox, LocalDateTime now) {
    if (!outbox.getTaskTemplate().isNotifyOnCompletion()) {
      outbox.cancel("태스크 완료 알림이 해제되었습니다.");
      return;
    }
    List<PushSubscriptionRecord> targets =
        subscriptions.findAllByMemberIdAndActiveTrue(outbox.getRecipient().getId());
    if (targets.isEmpty()) {
      outbox.cancel("활성화된 브라우저 알림 구독이 없습니다.");
      return;
    }

    String payload = payload(outbox);
    boolean delivered = false;
    String lastError = "";
    for (PushSubscriptionRecord target : targets) {
      WebPushSender.DeliveryResult result = sender.send(target, payload);
      if (result.successful()) {
        target.succeeded(now);
        delivered = true;
      } else {
        lastError = diagnostic(result);
        if (result.expired()) target.deactivate(lastError);
        else target.failed(lastError);
      }
    }
    if (delivered) outbox.sent(now);
    else outbox.retry(now, lastError);
  }

  private String payload(NotificationOutbox outbox) {
    try {
      return objectMapper.writeValueAsString(
          new Payload(
              "태스크 완료",
              "담당자가 태스크의 모든 항목을 완료했습니다.",
              "/tasks/" + outbox.getTaskTemplate().getId(),
              "task-completed-" + outbox.getScheduleId() + "-" + outbox.getScheduledDate()));
    } catch (JacksonException exception) {
      throw new IllegalStateException("알림 payload 직렬화에 실패했습니다.", exception);
    }
  }

  private String diagnostic(WebPushSender.DeliveryResult result) {
    String requestId = result.requestId().isBlank() ? "" : " requestId=" + result.requestId();
    return "status=" + result.statusCode() + " reason=" + result.reason() + requestId;
  }

  private record Payload(String title, String body, String url, String tag) {}
}
