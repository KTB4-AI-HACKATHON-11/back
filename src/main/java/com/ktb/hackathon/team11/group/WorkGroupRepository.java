package com.ktb.hackathon.team11.group;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkGroupRepository extends JpaRepository<WorkGroup, Long> {
  Optional<WorkGroup> findByInviteCode(String code);

  boolean existsByInviteCode(String code);
}
