package com.ktb.hackathon.team11.group;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkGroupRepository extends JpaRepository<WorkGroup, Long> {
  boolean existsByInviteCode(String code);
}
