package com.ktb.hackathon.team11.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, Long> {
  List<TaskTemplate> findAllByGroupIdAndActiveTrue(Long groupId);

  List<TaskTemplate> findAllByGroupIdAndActiveTrueOrderByCreatedAtDesc(Long groupId);

  long countByGroupIdAndActiveTrue(Long groupId);

  Optional<TaskTemplate> findByIdAndGroupId(Long id, Long groupId);
}
