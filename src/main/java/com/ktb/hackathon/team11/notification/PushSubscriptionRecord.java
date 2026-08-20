package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "push_subscriptions",
    indexes =
        @Index(
            name = "idx_push_subscriptions_endpoint_hash",
            columnList = "endpoint_hash",
            unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscriptionRecord extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Lob
  @Column(nullable = false, columnDefinition = "TEXT")
  private String endpoint;

  @Column(name = "endpoint_hash", nullable = false, length = 64, unique = true)
  private String endpointHash;

  @Column(nullable = false, length = 256)
  private String p256dh;

  @Column(nullable = false, length = 128)
  private String auth;

  private Long expirationTime;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private int failureCount;

  private LocalDateTime lastSuccessAt;

  @Column(length = 1000)
  private String lastError;

  public PushSubscriptionRecord(
      Member member,
      String endpoint,
      String endpointHash,
      String p256dh,
      String auth,
      Long expirationTime) {
    update(member, endpoint, p256dh, auth, expirationTime);
    this.endpointHash = endpointHash;
  }

  public void update(
      Member member, String endpoint, String p256dh, String auth, Long expirationTime) {
    this.member = member;
    this.endpoint = endpoint;
    this.p256dh = p256dh;
    this.auth = auth;
    this.expirationTime = expirationTime;
    active = true;
    failureCount = 0;
    lastError = null;
  }

  public void succeeded(LocalDateTime now) {
    active = true;
    failureCount = 0;
    lastSuccessAt = now;
    lastError = null;
  }

  public void failed(String error) {
    failureCount++;
    lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
  }

  public void deactivate(String error) {
    active = false;
    failed(error);
  }
}
