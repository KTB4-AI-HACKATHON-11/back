package com.ktb.hackathon.team11.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskPhotoRepository extends JpaRepository<TaskPhoto, Long> {
  boolean existsByGroupIdAndSha256(Long groupId, String sha);

  Optional<TaskPhoto> findByAttemptId(Long id);

  List<TaskPhoto> findAllByAttemptIdIn(Collection<Long> attemptIds);
}
