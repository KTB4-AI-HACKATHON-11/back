package com.ktb.hackathon.team11.group;

import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignmentRepository;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.task.TaskTemplateRepository;
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
  private final TaskTemplateRepository templates;
  private final TaskAssignmentRepository assignments;
  private final SecureRandom random = new SecureRandom();

  @Transactional
  public WorkGroup create(long managerId, String name, String description) {
    Member manager = members.requireRole(managerId, MemberRole.MANAGER);
    WorkGroup group = groups.save(new WorkGroup(name, description, createInviteCode(), manager));
    memberships.save(new GroupMember(group, manager));
    return group;
  }

  private String createInviteCode() {
    String code;
    do {
      code = String.format("%06d", random.nextInt(1_000_000));
    } while (groups.existsByInviteCode(code));
    return code;
  }

  @Transactional
  public WorkGroup join(long memberId, String inviteCode, Long legacyGroupId) {
    Member member = members.requireMember(memberId);
    WorkGroup group = requireJoinTarget(inviteCode, legacyGroupId);
    if (memberships.existsByGroupIdAndMemberId(group.getId(), memberId))
      throw new BusinessException(ErrorCode.ALREADY_GROUP_MEMBER);
    memberships.save(new GroupMember(group, member, MemberRole.WORKER));
    return group;
  }

  private WorkGroup requireJoinTarget(String inviteCode, Long legacyGroupId) {
    if (inviteCode != null && !inviteCode.isBlank()) {
      WorkGroup byInviteCode = groups.findByInviteCode(inviteCode).orElse(null);
      if (byInviteCode != null) return byInviteCode;

      // 기존 화면은 내부 PK를 000001처럼 채워 초대 번호로 공유했다. 배포 전 공유된 번호만
      // 한시적으로 살리되, 새로 발급된 실제 초대 코드를 항상 먼저 조회한다.
      if (inviteCode.startsWith("0")) {
        try {
          WorkGroup legacyGroup = groups.findById(Long.parseLong(inviteCode)).orElse(null);
          if (legacyGroup != null) return legacyGroup;
        } catch (NumberFormatException ignored) {
          // @Pattern 검증을 우회해 직접 호출된 경우에도 아래의 정상 도메인 오류로 처리한다.
        }
      }
      throw new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND);
    }
    if (legacyGroupId != null) return requireGroup(legacyGroupId);
    throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
  }

  public WorkGroup requireGroup(long id) {
    return groups.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
  }

  public GroupDetail detail(long groupId, long memberId) {
    GroupMember membership = requireMember(groupId, memberId);
    return detail(membership);
  }

  private GroupDetail detail(GroupMember membership) {
    WorkGroup group = membership.getGroup();
    long groupId = group.getId();
    long memberCount = memberships.countByGroupId(groupId);
    long managerCount = memberships.countByGroupIdAndGroupRole(groupId, MemberRole.MANAGER);
    long workerCount = memberships.countByGroupIdAndGroupRole(groupId, MemberRole.WORKER);
    long taskCount = templates.countByGroupIdAndActiveTrue(groupId);
    long assignmentCount = assignments.countByScheduleTaskTemplateGroupId(groupId);
    long completedCount =
        assignments.countByScheduleTaskTemplateGroupIdAndStatus(
            groupId, AssignmentStatus.COMPLETED);
    int completionRate =
        assignmentCount == 0 ? 0 : Math.round(completedCount * 100f / assignmentCount);
    return new GroupDetail(
        group,
        managerCount,
        workerCount,
        memberCount,
        taskCount,
        completionRate,
        membership.getGroupRole());
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

  public GroupMember requireWorker(long groupId, long memberId) {
    return memberships
        .findByGroupIdAndMemberId(groupId, memberId)
        .filter(membership -> membership.getGroupRole() == MemberRole.WORKER)
        .orElseThrow(() -> new BusinessException(ErrorCode.WORKER_NOT_IN_GROUP));
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

  public List<GroupDetail> groupDetailsOf(long memberId, int offset, int limit) {
    return groupsOf(memberId, offset, limit).stream().map(this::detail).toList();
  }

  public List<GroupMember> membersOf(long groupId, long requesterId) {
    requireMember(groupId, requesterId);
    return memberships.findAllByGroupId(groupId);
  }

  public record GroupDetail(
      WorkGroup group,
      long managerCount,
      long workerCount,
      long memberCount,
      long taskCount,
      int completionRate,
      MemberRole role) {}
}
