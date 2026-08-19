package com.ktb.hackathon.team11.task;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskItemTemplateRepository extends JpaRepository<TaskItemTemplate, Long> {
  List<TaskItemTemplate> findAllByTaskTemplateIdOrderBySequence(Long id);
}
