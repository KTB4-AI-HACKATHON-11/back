package com.ktb.hackathon.team11.notification;

import java.time.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
  Optional<NotificationOutbox> findByScheduleIdAndScheduledDateAndRecipientIdAndType(
      Long scheduleId, LocalDate scheduledDate, Long recipientId, NotificationType type);

  List<NotificationOutbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
      NotificationOutboxStatus status, LocalDateTime nextAttemptAt);

  List<NotificationOutbox> findAllByTaskTemplateIdAndStatusAndType(
      Long taskTemplateId, NotificationOutboxStatus status, NotificationType type);
}
