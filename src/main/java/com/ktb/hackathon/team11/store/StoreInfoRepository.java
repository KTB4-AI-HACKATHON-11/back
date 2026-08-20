package com.ktb.hackathon.team11.store;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreInfoRepository extends JpaRepository<StoreInfo, Long> {
  List<StoreInfo> findAllByGroupIdOrderByCategoryAscIdAsc(Long groupId);

  Optional<StoreInfo> findByIdAndGroupId(Long id, Long groupId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select info from StoreInfo info where info.group.id = :groupId order by info.category, info.id")
  List<StoreInfo> lockAllByGroupId(@Param("groupId") Long groupId);
}
