package com.ktb.hackathon.team11.group;

import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import com.ktb.hackathon.team11.member.MemberRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "2. 그룹", description = "편의점 그룹 생성, 초대 코드 가입, 소속 조회 API")
public class GroupController {
  private final GroupService service;
  private final SessionService sessions;

  @Operation(
      summary = "그룹 생성",
      description = "MANAGER가 그룹을 만들고 알바생 초대에 사용할 고유한 6자리 코드를 발급받습니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "그룹 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "MANAGER가 아닌 회원")
      })
  @PostMapping("/groups")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<GroupResponse> create(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Valid @RequestBody CreateGroupRequest request) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "GROUP_CREATED",
        GroupResponse.from(
            service.create(request.managerId(), request.name(), request.description())));
  }

  @Operation(
      summary = "초대 코드로 그룹 가입",
      description = "회원이 관리자로부터 전달받은 6자리 초대 코드를 입력해 그룹에 가입합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "그룹 가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 초대 코드"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 가입한 그룹")
      })
  @PostMapping("/groups/join")
  ApiResponse<GroupResponse> join(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Valid @RequestBody JoinGroupRequest request) {
    sessions.require(token, request.memberId());
    return ApiResponse.of(
        "GROUP_JOINED",
        GroupResponse.from(
            service.join(request.memberId(), request.inviteCode(), request.groupId())));
  }

  @Operation(
      summary = "그룹 상세 조회",
      description = "그룹 구성원이 그룹의 기본 정보와 본인의 그룹 내 역할을 조회합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "그룹 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "그룹 멤버가 아님"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "존재하지 않는 그룹")
      })
  @GetMapping("/groups/{groupId}")
  ApiResponse<GroupDetailResponse> detail(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Parameter(description = "그룹 ID", example = "1") @PathVariable long groupId,
      @Parameter(description = "조회 회원 ID", example = "2") @RequestParam long memberId) {
    sessions.require(token, memberId);
    return ApiResponse.of(
        "SUCCESS",
        "그룹 상세 조회 성공",
        GroupDetailResponse.from(service.detail(groupId, memberId)));
  }

  @Operation(summary = "내 그룹 목록 조회", description = "회원이 가입한 편의점 그룹을 offset과 limit 기준으로 잘라 조회합니다.")
  @GetMapping("/members/{memberId}/groups")
  ApiResponse<List<GroupDetailResponse>> mine(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Parameter(description = "조회할 회원 ID", example = "2") @PathVariable long memberId,
      @Parameter(description = "건너뛸 그룹 수", example = "0") @RequestParam(defaultValue = "0")
          @PositiveOrZero
          int offset,
      @Parameter(description = "조회할 그룹 수", example = "20") @RequestParam(defaultValue = "20")
          @Positive
          int limit) {
    sessions.require(token, memberId);
    return ApiResponse.of(
        "GROUPS_FOUND",
        service.groupDetailsOf(memberId, offset, limit).stream()
            .map(GroupDetailResponse::from)
            .toList());
  }

  @Operation(
      summary = "그룹 구성원 조회",
      description = "그룹 구성원이 그룹에 가입한 관리자와 알바생 목록을 조회할 수 있습니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "구성원 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "그룹 구성원 권한 없음")
      })
  @GetMapping("/groups/{groupId}/members")
  ApiResponse<List<GroupMemberResponse>> members(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Parameter(description = "그룹 ID", example = "1") @PathVariable long groupId,
      @Parameter(description = "요청 관리자 ID", example = "1") @RequestParam long requesterId) {
    sessions.require(token, requesterId);
    return ApiResponse.of(
        "GROUP_MEMBERS_FOUND",
        service.membersOf(groupId, requesterId).stream()
            .map(
                groupMember ->
                    new GroupMemberResponse(
                        groupMember.getMember().getId(),
                        groupMember.getMember().getNickname(),
                        groupMember.getGroupRole()))
            .toList());
  }

  @Schema(description = "그룹 생성 요청")
  public record CreateGroupRequest(
      @Schema(description = "그룹을 생성하는 MANAGER ID", example = "1") @NotNull Long managerId,
      @Schema(description = "편의점 또는 근무 그룹명", example = "모아모아 편의점 야간조") @NotBlank @Size(max = 80)
          String name,
      @Schema(description = "그룹에서 관리할 업무 설명", example = "야간 근무 및 오픈 준비 업무")
          @Size(max = 200)
          String description) {}

  @Schema(description = "그룹 가입 요청")
  public record JoinGroupRequest(
      @Schema(description = "가입할 회원 ID", example = "2") @NotNull Long memberId,
      @Schema(description = "관리자가 공유한 6자리 초대 코드", example = "482731")
          @Pattern(regexp = "\\d{6}", message = "초대 코드는 6자리 숫자여야 합니다.")
          String inviteCode,
      @Schema(description = "이전 앱 호환용 내부 그룹 ID", example = "1", deprecated = true)
          @Positive
          Long groupId) {
    @AssertTrue(message = "초대 코드를 입력해주세요.")
    public boolean hasJoinTarget() {
      return (inviteCode != null && !inviteCode.isBlank()) || groupId != null;
    }
  }

  @Schema(description = "그룹 정보")
  public record GroupResponse(
      @Schema(description = "그룹 ID", example = "1") Long groupId,
      @Schema(description = "그룹 가입용 6자리 초대 코드", example = "482731") String inviteCode,
      @Schema(description = "그룹명", example = "모아모아 편의점 야간조") String name,
      @Schema(description = "그룹 설명", example = "야간 근무 및 오픈 준비 업무") String description) {
    static GroupResponse from(WorkGroup group) {
      return new GroupResponse(
          group.getId(), group.getInviteCode(), group.getName(), group.getDescription());
    }
  }

  @Schema(description = "그룹 상세 정보")
  public record GroupDetailResponse(
      @Schema(description = "그룹 ID", example = "482731") Long groupId,
      @Schema(description = "그룹 가입용 6자리 초대 코드", example = "731482") String inviteCode,
      @Schema(description = "그룹명", example = "성수 플래그십 스토어") String name,
      @Schema(description = "그룹 설명", example = "오픈 준비부터 마감 점검까지 현장 운영 업무를 관리합니다.")
          String description,
      @Schema(description = "그룹 관리자 수", example = "1") long managerCount,
      @Schema(description = "그룹 알바생 수", example = "7") long workerCount,
      @Schema(description = "그룹 전체 인원 수", example = "8") long memberCount,
      @Schema(description = "활성 태스크 수", example = "3") long taskCount,
      @Schema(description = "전체 체크리스트 완료율(%)", example = "58") int completionRate,
      @Schema(description = "조회 회원의 그룹 내 역할", example = "MANAGER") MemberRole role) {
    static GroupDetailResponse from(GroupService.GroupDetail detail) {
      WorkGroup group = detail.group();
      return new GroupDetailResponse(
          group.getId(),
          group.getInviteCode(),
          group.getName(),
          group.getDescription(),
          detail.managerCount(),
          detail.workerCount(),
          detail.memberCount(),
          detail.taskCount(),
          detail.completionRate(),
          detail.role());
    }
  }

  @Schema(description = "그룹 구성원 정보")
  public record GroupMemberResponse(
      @Schema(description = "회원 ID", example = "2") Long memberId,
      @Schema(description = "닉네임", example = "야간알바") String nickname,
      @Schema(description = "그룹 내 역할", example = "WORKER") MemberRole role) {}
}
