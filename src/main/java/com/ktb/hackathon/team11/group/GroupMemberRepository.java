package com.ktb.hackathon.team11.group;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GroupMemberRepository extends JpaRepository<GroupMember,Long> {
    boolean existsByGroupIdAndMemberId(Long groupId, Long memberId);
    Optional<GroupMember> findByGroupIdAndMemberId(Long groupId, Long memberId);
    List<GroupMember> findAllByMemberId(Long memberId);
    List<GroupMember> findAllByGroupId(Long groupId);
}
