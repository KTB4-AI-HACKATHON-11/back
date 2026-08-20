package com.ktb.hackathon.team11.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreInfoRepository extends JpaRepository<StoreInfo, Long> {
  List<StoreInfo> findAllByGroupIdOrderByCategoryAscIdAsc(Long groupId);

  Optional<StoreInfo> findByIdAndGroupId(Long id, Long groupId);
}
