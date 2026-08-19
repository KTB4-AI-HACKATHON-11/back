package com.ktb.hackathon.team11.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByNickname(String nickname);

  boolean existsByNickname(String nickname);
}
