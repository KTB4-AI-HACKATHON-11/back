package com.ktb.hackathon.team11.group;

import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
  boolean existsByGroupIdAndMemberId(Long groupId, Long memberId);

  Optional<GroupMember> findByGroupIdAndMemberId(Long groupId, Long memberId);

  @EntityGraph(attributePaths = "group")
  List<GroupMember> findAllByMemberId(Long memberId);

  @EntityGraph(attributePaths = "member")
  List<GroupMember> findAllByGroupId(Long groupId);
}
