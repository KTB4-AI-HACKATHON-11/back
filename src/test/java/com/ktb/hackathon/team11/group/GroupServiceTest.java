package com.ktb.hackathon.team11.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.member.MemberRole;
import com.ktb.hackathon.team11.member.MemberService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

  @Mock private WorkGroupRepository groups;
  @Mock private GroupMemberRepository memberships;
  @Mock private MemberService members;
  @InjectMocks private GroupService service;

  @Test
  void createsGroupWithNameAndDescriptionAndRegistersManager() {
    Member manager = new Member("점장", MemberRole.MANAGER);
    when(members.requireRole(1L, MemberRole.MANAGER)).thenReturn(manager);
    when(groups.existsByInviteCode(any())).thenReturn(false);
    when(groups.save(any(WorkGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

    WorkGroup group = service.create(1L, "  성수점  ", "  야간 업무 관리  ");

    assertThat(group.getName()).isEqualTo("성수점");
    assertThat(group.getDescription()).isEqualTo("야간 업무 관리");
    assertThat(group.getInviteCode()).matches("\\d{6}");

    ArgumentCaptor<GroupMember> membership = ArgumentCaptor.forClass(GroupMember.class);
    verify(memberships).save(membership.capture());
    assertThat(membership.getValue().getGroup()).isSameAs(group);
    assertThat(membership.getValue().getMember()).isSameAs(manager);
    assertThat(membership.getValue().getGroupRole()).isEqualTo(MemberRole.MANAGER);
  }

  @Test
  void normalizesBlankDescriptionToNull() {
    Member manager = new Member("점장", MemberRole.MANAGER);
    when(members.requireRole(1L, MemberRole.MANAGER)).thenReturn(manager);
    when(groups.existsByInviteCode(any())).thenReturn(false);
    when(groups.save(any(WorkGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

    WorkGroup group = service.create(1L, "성수점", "   ");

    assertThat(group.getDescription()).isNull();
  }

  @Test
  void joinsGroupByGroupId() {
    Member worker = new Member("알바", MemberRole.WORKER);
    WorkGroup group =
        new WorkGroup("성수점", "야간 업무", "000001", new Member("점장", MemberRole.MANAGER));
    when(members.requireMember(2L)).thenReturn(worker);
    when(groups.findById(10L)).thenReturn(Optional.of(group));
    when(memberships.existsByGroupIdAndMemberId(10L, 2L)).thenReturn(false);

    WorkGroup joined = service.join(2L, 10L);

    assertThat(joined).isSameAs(group);
    verify(memberships).save(any(GroupMember.class));
  }

  @Test
  void rejectsDuplicateGroupMembership() {
    Member worker = new Member("알바", MemberRole.WORKER);
    WorkGroup group =
        new WorkGroup("성수점", null, "000001", new Member("점장", MemberRole.MANAGER));
    when(members.requireMember(2L)).thenReturn(worker);
    when(groups.findById(10L)).thenReturn(Optional.of(group));
    when(memberships.existsByGroupIdAndMemberId(10L, 2L)).thenReturn(true);

    assertThatThrownBy(() -> service.join(2L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.ALREADY_GROUP_MEMBER);
  }

  @Test
  void returnsGroupDetailAfterCheckingMembership() {
    WorkGroup group =
        new WorkGroup("성수점", "매장 운영", "000001", new Member("점장", MemberRole.MANAGER));
    when(memberships.findByGroupIdAndMemberId(10L, 2L))
        .thenReturn(Optional.of(new GroupMember(group, new Member("알바", MemberRole.WORKER))));
    when(groups.findById(10L)).thenReturn(Optional.of(group));
    when(memberships.countByGroupId(10L)).thenReturn(8L);

    GroupService.GroupDetail result = service.detail(10L, 2L);

    assertThat(result.group()).isSameAs(group);
    assertThat(result.memberCount()).isEqualTo(8L);
    assertThat(result.role()).isEqualTo(MemberRole.WORKER);
    verify(memberships).findByGroupIdAndMemberId(10L, 2L);
  }

  @Test
  void listsMemberGroupsWithOffsetAndLimit() {
    Member worker = new Member("알바", MemberRole.WORKER);
    GroupMember membership1 =
        new GroupMember(
            new WorkGroup("성수점", "야간 업무", "000001", new Member("점장", MemberRole.MANAGER)),
            worker);
    GroupMember membership2 =
        new GroupMember(
            new WorkGroup("건대점", "오픈 업무", "000002", new Member("부점장", MemberRole.MANAGER)),
            worker);
    GroupMember membership3 =
        new GroupMember(
            new WorkGroup("잠실점", "마감 업무", "000003", new Member("매니저", MemberRole.MANAGER)),
            worker);
    when(members.requireMember(2L)).thenReturn(worker);
    when(memberships.findAllByMemberIdOrderByIdDesc(org.mockito.ArgumentMatchers.eq(2L), any(Pageable.class)))
        .thenReturn(List.of(membership1, membership2, membership3));

    List<GroupMember> result = service.groupsOf(2L, 1, 2);

    assertThat(result).containsExactly(membership2, membership3);
  }
}
