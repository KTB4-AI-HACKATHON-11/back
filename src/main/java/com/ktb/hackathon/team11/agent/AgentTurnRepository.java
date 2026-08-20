package com.ktb.hackathon.team11.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTurnRepository extends JpaRepository<AgentTurn, Long> {
  @EntityGraph(attributePaths = {"manager", "group"})
  Optional<AgentTurn> findByGroupIdAndRequestId(Long groupId, String requestId);

  @EntityGraph(attributePaths = "manager")
  List<AgentTurn> findAllByGroupIdOrderByIdDesc(Long groupId, Pageable pageable);

  @EntityGraph(attributePaths = {"manager", "group"})
  Optional<AgentTurn> findWithManagerById(Long id);
}
