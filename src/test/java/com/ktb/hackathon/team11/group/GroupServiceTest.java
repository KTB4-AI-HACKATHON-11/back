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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
