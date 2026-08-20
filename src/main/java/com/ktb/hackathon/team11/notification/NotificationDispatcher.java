package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignmentRepository;
import com.ktb.hackathon.team11.task.TaskRunId;
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
  private final NotificationDeliveryRepository deliveries;
  private final PushSubscriptionRepository subscriptions;
  private final TaskAssignmentRepository assignments;
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
    var runAssignments =
        assignments.findAllByScheduleIdAndScheduledDate(
            outbox.getScheduleId(), outbox.getScheduledDate());
    if (outbox.getType() == NotificationType.TASK_COMPLETED
        && (!outbox.getTaskTemplate().isNotifyOnCompletion()
            || runAssignments.isEmpty()
            || runAssignments.stream()
                .anyMatch(assignment -> assignment.getStatus() != AssignmentStatus.COMPLETED))) {
      outbox.cancel("완료 알림 조건이 더 이상 유효하지 않습니다.");
      return;
    }
    if (outbox.getType() == NotificationType.MANAGER_REVIEW_REQUESTED
        && runAssignments.stream()
            .noneMatch(
                assignment ->
                    assignment.getStatus() == AssignmentStatus.MANAGER_REVIEW_REQUESTED)) {
      outbox.cancel("매니저 확인 요청이 이미 처리되었습니다.");
      return;
    }
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
                    () -> deliveries.save(new NotificationDelivery(outbox, target, now))));

    String payload = payload(outbox);
    String lastError = "";
    for (NotificationDelivery delivery : deliveries.findAllByOutboxId(outbox.getId())) {
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
    List<NotificationDelivery> results = deliveries.findAllByOutboxId(outbox.getId());
    if (results.stream().anyMatch(item -> item.getStatus() == NotificationDeliveryStatus.PENDING)) {
      outbox.retry(now, lastError.isBlank() ? "일부 브라우저 전달 재시도 대기 중입니다." : lastError);
    } else if (results.stream().anyMatch(item -> item.getStatus() == NotificationDeliveryStatus.SENT)) {
      outbox.sent(now);
    } else {
      outbox.cancel("전달 가능한 브라우저 구독이 없습니다.");
    }
  }

  private String payload(NotificationOutbox outbox) {
    String groupName = abbreviate(outbox.getTaskTemplate().getGroup().getName(), 32);
    String taskTitle = abbreviate(outbox.getTaskTemplate().getTitle(), 48);
    try {
      if (outbox.getType() == NotificationType.MANAGER_REVIEW_REQUESTED) {
        return objectMapper.writeValueAsString(
            new Payload(
                groupName + " · 확인 요청",
                "‘" + taskTitle + "’ 업무의 사진을 확인해 주세요.",
                "/groups/" + outbox.getTaskTemplate().getGroup().getId(),
                "manager-review-" + outbox.getScheduleId() + "-" + outbox.getScheduledDate()));
      }
      return objectMapper.writeValueAsString(
          new Payload(
              groupName + " · 업무 완료",
              "‘" + taskTitle + "’ 업무의 모든 항목이 완료됐습니다.",
              "/task-runs/"
                  + new TaskRunId(outbox.getScheduleId(), outbox.getScheduledDate()).value(),
              "task-completed-" + outbox.getScheduleId() + "-" + outbox.getScheduledDate()));
    } catch (JacksonException exception) {
      throw new IllegalStateException("알림 payload 직렬화에 실패했습니다.", exception);
    }
  }

  private String abbreviate(String value, int maxCodePoints) {
    int length = value.codePointCount(0, value.length());
    if (length <= maxCodePoints) return value;
    int endIndex = value.offsetByCodePoints(0, maxCodePoints - 1);
    return value.substring(0, endIndex) + "…";
  }

  private String diagnostic(WebPushSender.DeliveryResult result) {
    String requestId = result.requestId().isBlank() ? "" : " requestId=" + result.requestId();
    return "status=" + result.statusCode() + " reason=" + result.reason() + requestId;
  }

  private record Payload(String title, String body, String url, String tag) {}
}
