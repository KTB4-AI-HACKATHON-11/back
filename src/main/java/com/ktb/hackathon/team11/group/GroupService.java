package com.ktb.hackathon.team11.group;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.*;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
  private final WorkGroupRepository groups;
  private final GroupMemberRepository memberships;
  private final MemberService members;
  private final SecureRandom random = new SecureRandom();

  @Transactional
  public WorkGroup create(long managerId, String name, String description) {
    Member manager = members.requireRole(managerId, MemberRole.MANAGER);
    WorkGroup group =
        groups.save(new WorkGroup(name, description, createLegacyCode(), manager));
    memberships.save(new GroupMember(group, manager));
    return group;
  }

  private String createLegacyCode() {
    String code;
    do {
      code = String.format("%06d", random.nextInt(1_000_000));
    } while (groups.existsByInviteCode(code));
    return code;
  }

  @Transactional
  public WorkGroup join(long memberId, long groupId) {
    Member member = members.requireMember(memberId);
    WorkGroup group = requireGroup(groupId);
    if (memberships.existsByGroupIdAndMemberId(groupId, memberId))
      throw new BusinessException(ErrorCode.ALREADY_GROUP_MEMBER);
    memberships.save(new GroupMember(group, member));
    return group;
  }

  public WorkGroup requireGroup(long id) {
    return groups.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
  }

  public WorkGroup detail(long groupId, long memberId) {
    requireMember(groupId, memberId);
    return requireGroup(groupId);
  }

  public GroupMember requireMember(long groupId, long memberId) {
    return memberships
        .findByGroupIdAndMemberId(groupId, memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACCESS_DENIED));
  }

  public GroupMember requireManager(long groupId, long memberId) {
    GroupMember gm = requireMember(groupId, memberId);
    if (gm.getGroupRole() != MemberRole.MANAGER)
      throw new BusinessException(ErrorCode.MANAGER_REQUIRED);
    return gm;
  }

  public List<GroupMember> groupsOf(long memberId) {
    members.requireMember(memberId);
    return memberships.findAllByMemberId(memberId);
  }

  public List<GroupMember> groupsOf(long memberId, int offset, int limit) {
    members.requireMember(memberId);
    int page = offset / limit;
    int remainder = offset % limit;
    int pageSize = limit + remainder;

    return memberships.findAllByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, pageSize)).stream()
        .skip(remainder)
        .limit(limit)
        .toList();
  }

  public List<GroupMember> membersOf(long groupId, long requesterId) {
    requireManager(groupId, requesterId);
    return memberships.findAllByGroupId(groupId);
  }
}
