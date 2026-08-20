package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.group.WorkGroup;
import com.ktb.hackathon.team11.member.Member;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentNotificationService {
  private final GroupService groups;
  private final AgentNotificationOutboxRepository outboxes;
  private final Clock clock;

  @Transactional
  public List<QueuedNotification> queue(
      long groupId, long managerId, List<Long> recipientIds, String message) {
    WorkGroup group = groups.requireManager(groupId, managerId).getGroup();
    if (recipientIds == null
        || recipientIds.isEmpty()
        || recipientIds.size() > 20
        || message == null
        || message.isBlank()
        || message.strip().length() > 300) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }

    String cleanMessage = message.strip();
    LocalDateTime now = LocalDateTime.now(clock);
    return new LinkedHashSet<>(recipientIds).stream()
        .map(
            recipientId -> {
              Member recipient = groups.requireWorker(groupId, recipientId).getMember();
              outboxes.save(
                  new AgentNotificationOutbox(
                      recipient,
                      abbreviate(group.getName(), 32) + " · 매니저 알림",
                      cleanMessage,
                      "/groups/" + groupId,
                      "agent-message-" + UUID.randomUUID(),
                      now));
              return new QueuedNotification(
                  recipient.getId(), recipient.getNickname(), cleanMessage);
            })
        .toList();
  }

  private String abbreviate(String value, int maxCodePoints) {
    int length = value.codePointCount(0, value.length());
    if (length <= maxCodePoints) return value;
    int endIndex = value.offsetByCodePoints(0, maxCodePoints - 1);
    return value.substring(0, endIndex) + "…";
  }

  public record QueuedNotification(long memberId, String nickname, String message) {}
}
