package com.ktb.hackathon.team11.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository
    extends JpaRepository<NotificationDelivery, Long> {
  Optional<NotificationDelivery> findByOutboxIdAndSubscriptionId(
      Long outboxId, Long subscriptionId);

  @EntityGraph(attributePaths = "subscription")
  List<NotificationDelivery> findAllByOutboxId(Long outboxId);
}
