package com.ktb.hackathon.team11.group;

import com.ktb.hackathon.team11.global.response.ApiResponse;
import com.ktb.hackathon.team11.member.MemberRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class GroupController {
    private final GroupService service;
    @PostMapping("/groups") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<GroupResponse> create(@Valid @RequestBody CreateGroupRequest r){return ApiResponse.of("GROUP_CREATED",GroupResponse.from(service.create(r.managerId(),r.name())));}
    @PostMapping("/groups/join") ApiResponse<GroupResponse> join(@Valid @RequestBody JoinGroupRequest r){return ApiResponse.of("GROUP_JOINED",GroupResponse.from(service.join(r.memberId(),r.inviteCode())));}
    @GetMapping("/members/{memberId}/groups") ApiResponse<List<GroupResponse>> mine(@PathVariable long memberId){return ApiResponse.of("GROUPS_FOUND",service.groupsOf(memberId).stream().map(g->GroupResponse.from(g.getGroup())).toList());}
    @GetMapping("/groups/{groupId}/members") ApiResponse<List<GroupMemberResponse>> members(@PathVariable long groupId,@RequestParam long requesterId){return ApiResponse.of("GROUP_MEMBERS_FOUND",service.membersOf(groupId,requesterId).stream().map(g->new GroupMemberResponse(g.getMember().getId(),g.getMember().getNickname(),g.getGroupRole())).toList());}
    public record CreateGroupRequest(@NotNull Long managerId,@NotBlank @Size(max=80) String name){}
    public record JoinGroupRequest(@NotNull Long memberId,@Pattern(regexp="\\d{6}") String inviteCode){}
    public record GroupResponse(Long groupId,String name,String inviteCode){static GroupResponse from(WorkGroup g){return new GroupResponse(g.getId(),g.getName(),g.getInviteCode());}}
    public record GroupMemberResponse(Long memberId,String nickname,MemberRole role){}
}
