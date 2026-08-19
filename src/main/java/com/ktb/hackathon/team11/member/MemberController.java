package com.ktb.hackathon.team11.member;

import com.ktb.hackathon.team11.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService service;

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<MemberResponse> create(@Valid @RequestBody CreateMemberRequest request) {
        return ApiResponse.of("MEMBER_CREATED", MemberResponse.from(service.create(request.nickname(), request.role())));
    }

    @PostMapping("/login")
    ApiResponse<MemberResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of("LOGIN_SUCCESS", MemberResponse.from(service.login(request.nickname())));
    }

    public record CreateMemberRequest(@NotBlank @Size(max=30) String nickname, @NotNull MemberRole role) {}
    public record LoginRequest(@NotBlank String nickname) {}
    public record MemberResponse(Long memberId, String nickname, MemberRole role) {
        static MemberResponse from(Member m) { return new MemberResponse(m.getId(), m.getNickname(), m.getRole()); }
    }
}
