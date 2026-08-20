package com.ktb.hackathon.team11.notification;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository
    extends JpaRepository<PushSubscriptionRecord, Long> {
  Optional<PushSubscriptionRecord> findByEndpointHash(String endpointHash);

  List<PushSubscriptionRecord> findAllByMemberIdAndActiveTrue(Long memberId);
}
