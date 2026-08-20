package com.ktb.hackathon.team11.auth;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {
  public static final String COOKIE_NAME = "CHECKON_SESSION";

  private final MemberSessionRepository sessions;
  private final SecureRandom random = new SecureRandom();

  @Value("${auth.session-days:7}") private long sessionDays;
  @Value("${auth.cookie-secure:false}") private boolean cookieSecure;

  @Transactional
  public IssuedSession issue(Member member) {
    byte[] tokenBytes = new byte[32];
    random.nextBytes(tokenBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(sessionDays);
    sessions.save(new MemberSession(hash(token), member, expiresAt));
    return new IssuedSession(token, expiresAt);
  }

  @Transactional
  public Member require(String token) {
    if (token == null || token.isBlank()) throw new BusinessException(ErrorCode.SESSION_REQUIRED);
    MemberSession session =
        sessions
            .findByTokenHash(hash(token))
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_REQUIRED));
    if (session.isExpired(LocalDateTime.now())) {
      sessions.delete(session);
      throw new BusinessException(ErrorCode.SESSION_REQUIRED);
    }
    return session.getMember();
  }

  @Transactional
  public Member require(String token, long claimedMemberId) {
    Member member = require(token);
    if (!member.getId().equals(claimedMemberId)) {
      throw new BusinessException(ErrorCode.SESSION_MEMBER_MISMATCH);
    }
    return member;
  }

  @Transactional
  public void revoke(String token) {
    if (token != null && !token.isBlank()) sessions.deleteByTokenHash(hash(token));
  }

  public ResponseCookie cookie(String token) {
    return ResponseCookie.from(COOKIE_NAME, token)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofDays(sessionDays))
        .build();
  }

  public ResponseCookie clearCookie() {
    return ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  @Scheduled(cron = "0 17 3 * * *", zone = "Asia/Seoul")
  @Transactional
  public void removeExpired() {
    sessions.deleteAllByExpiresAtBefore(LocalDateTime.now());
  }

  private String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record IssuedSession(String token, LocalDateTime expiresAt) {}
}
