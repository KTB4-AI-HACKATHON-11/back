package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.group.GroupMemberRepository;
import com.ktb.hackathon.team11.member.MemberRole;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerReviewNotificationService {
  private final GroupMemberRepository memberships;
  private final NotificationOutboxRepository outboxes;
  private final NotificationDeliveryRepository deliveries;
  private final Clock clock;

  @Transactional
  public void requested(TaskAssignment assignment) {
    var template = assignment.getSchedule().getTaskTemplate();
    memberships
        .findAllByGroupIdAndGroupRole(template.getGroup().getId(), MemberRole.MANAGER)
        .forEach(
            membership -> {
              Long recipientId = membership.getMember().getId();
              LocalDateTime now = LocalDateTime.now(clock);
              var existing =
                  outboxes.findByScheduleIdAndScheduledDateAndRecipientIdAndType(
                      assignment.getSchedule().getId(),
                      assignment.getScheduledDate(),
                      recipientId,
                      NotificationType.MANAGER_REVIEW_REQUESTED);
              if (existing.isPresent()) {
                existing.get().reopen(now);
                deliveries.findAllByOutboxId(existing.get().getId()).stream()
                    .filter(delivery -> delivery.getSubscription().isActive())
                    .forEach(delivery -> delivery.reopen(now));
                return;
              }
              outboxes.save(
                  new NotificationOutbox(
                      assignment.getSchedule().getId(),
                      assignment.getScheduledDate(),
                      template,
                      membership.getMember(),
                      NotificationType.MANAGER_REVIEW_REQUESTED,
                      now));
            });
  }
}
