package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.Member;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {
  private final PushSubscriptionRepository subscriptions;

  @Transactional
  public PushSubscriptionRecord upsert(
      Member member,
      String endpoint,
      String p256dh,
      String auth,
      Long expirationTime) {
    validate(endpoint, p256dh, auth);
    String endpointHash = hash(endpoint);
    PushSubscriptionRecord subscription =
        subscriptions
            .findByEndpointHash(endpointHash)
            .orElseGet(
                () ->
                    new PushSubscriptionRecord(
                        member, endpoint, endpointHash, p256dh, auth, expirationTime));
    subscription.update(member, endpoint, p256dh, auth, expirationTime);
    return subscriptions.save(subscription);
  }

  @Transactional
  public void deactivate(Member member, String endpointHash) {
    PushSubscriptionRecord subscription =
        subscriptions
            .findByEndpointHash(endpointHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!subscription.getMember().getId().equals(member.getId()))
      throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
    subscription.deactivate("사용자가 브라우저 알림을 해제했습니다.");
  }

  public String endpointHash(String endpoint) {
    return hash(endpoint);
  }

  private void validate(String endpoint, String p256dh, String auth) {
    try {
      URI uri = URI.create(endpoint);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
        throw new IllegalArgumentException();
    } catch (RuntimeException exception) {
      throw new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
    }
    if (endpoint.length() > 4096
        || p256dh == null
        || p256dh.isBlank()
        || p256dh.length() > 256
        || auth == null
        || auth.isBlank()
        || auth.length() > 128)
      throw new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
