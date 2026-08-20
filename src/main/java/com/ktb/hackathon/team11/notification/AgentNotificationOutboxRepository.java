package com.ktb.hackathon.team11.notification;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentNotificationOutboxRepository
    extends JpaRepository<AgentNotificationOutbox, Long> {
  @EntityGraph(attributePaths = "recipient")
  List<AgentNotificationOutbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
      NotificationOutboxStatus status, LocalDateTime nextAttemptAt);
}
