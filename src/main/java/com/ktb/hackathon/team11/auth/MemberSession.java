package com.ktb.hackathon.team11.auth;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "member_sessions",
    indexes = @Index(name = "idx_member_sessions_token_hash", columnList = "token_hash", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSession extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "token_hash", nullable = false, length = 64, unique = true)
  private String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  public MemberSession(String tokenHash, Member member, LocalDateTime expiresAt) {
    this.tokenHash = tokenHash;
    this.member = member;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(LocalDateTime now) {
    return !expiresAt.isAfter(now);
  }
}
