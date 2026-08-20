package com.ktb.hackathon.team11.notification;

import java.time.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
  boolean existsByScheduleIdAndScheduledDateAndRecipientIdAndType(
      Long scheduleId, LocalDate scheduledDate, Long recipientId, NotificationType type);

  List<NotificationOutbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
      NotificationOutboxStatus status, LocalDateTime nextAttemptAt);

  List<NotificationOutbox> findAllByTaskTemplateIdAndStatus(
      Long taskTemplateId, NotificationOutboxStatus status);
}
