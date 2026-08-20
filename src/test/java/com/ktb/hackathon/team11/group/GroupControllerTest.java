package com.ktb.hackathon.team11.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.hackathon.team11.global.response.ApiResponse;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.member.MemberRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

  @Mock private GroupService service;
  @InjectMocks private GroupController controller;

  @Test
  void createsGroupWithNameAndDescription() {
    WorkGroup group =
        new WorkGroup(
            "성수 플래그십 스토어",
            "야간 근무 및 오픈 준비 업무",
            "000001",
            new Member("점장", MemberRole.MANAGER));
    when(service.create(1L, "성수 플래그십 스토어", "야간 근무 및 오픈 준비 업무"))
        .thenReturn(group);

    ApiResponse<GroupController.GroupResponse> response =
        controller.create(
            new GroupController.CreateGroupRequest(
                1L, "성수 플래그십 스토어", "야간 근무 및 오픈 준비 업무"));

    assertThat(response.getCode()).isEqualTo("GROUP_CREATED");
    assertThat(response.getData().name()).isEqualTo("성수 플래그십 스토어");
    assertThat(response.getData().description()).isEqualTo("야간 근무 및 오픈 준비 업무");
  }

  @Test
  void joinsGroupByGroupId() {
    WorkGroup group =
        new WorkGroup("성수점", null, "000001", new Member("점장", MemberRole.MANAGER));
    when(service.join(2L, 10L)).thenReturn(group);

    ApiResponse<GroupController.GroupResponse> response =
        controller.join(new GroupController.JoinGroupRequest(2L, 10L));

    assertThat(response.getCode()).isEqualTo("GROUP_JOINED");
    verify(service).join(2L, 10L);
  }

  @Test
  void returnsGroupDetail() {
    WorkGroup group =
        new WorkGroup("성수 플래그십 스토어", "매장 운영", "000001", new Member("점장", MemberRole.MANAGER));
    when(service.detail(1L, 2L)).thenReturn(group);

    ApiResponse<GroupController.GroupResponse> response = controller.detail(1L, 2L);

    assertThat(response.getCode()).isEqualTo("GROUP_FOUND");
    assertThat(response.getData().name()).isEqualTo("성수 플래그십 스토어");
    assertThat(response.getData().description()).isEqualTo("매장 운영");
    verify(service).detail(1L, 2L);
  }

  @Test
  void listsMyGroupsWithOffsetAndLimit() {
    GroupMember first =
        new GroupMember(
            new WorkGroup("성수점", "야간 업무", "000001", new Member("점장", MemberRole.MANAGER)),
            new Member("알바", MemberRole.WORKER));
    GroupMember second =
        new GroupMember(
            new WorkGroup("건대점", "오픈 업무", "000002", new Member("부점장", MemberRole.MANAGER)),
            new Member("알바", MemberRole.WORKER));
    when(service.groupsOf(7L, 5, 2)).thenReturn(List.of(first, second));

    ApiResponse<List<GroupController.GroupResponse>> response = controller.mine(7L, 5, 2);

    assertThat(response.getCode()).isEqualTo("GROUPS_FOUND");
    assertThat(response.getData()).hasSize(2);
    assertThat(response.getData().get(0).name()).isEqualTo("성수점");
    assertThat(response.getData().get(1).name()).isEqualTo("건대점");
    verify(service).groupsOf(7L, 5, 2);
  }
}
