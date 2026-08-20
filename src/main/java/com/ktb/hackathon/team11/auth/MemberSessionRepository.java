package com.ktb.hackathon.team11.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface MemberSessionRepository extends JpaRepository<MemberSession, Long> {
  @EntityGraph(attributePaths = "member")
  Optional<MemberSession> findByTokenHash(String tokenHash);

  void deleteByTokenHash(String tokenHash);

  void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
