package com.ktb.hackathon.team11.member;

import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "1. 회원", description = "데모 회원가입과 닉네임 로그인 API")
public class MemberController {
  private final MemberService service;

  @Operation(
      summary = "회원가입",
      description = "닉네임과 역할을 등록합니다. MANAGER는 그룹과 업무를 관리하고 WORKER는 배정 업무를 수행합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "닉네임 또는 역할 형식 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 사용 중인 닉네임")
      })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<MemberResponse> create(@Valid @RequestBody CreateMemberRequest request) {
    return ApiResponse.of(
        "MEMBER_CREATED", MemberResponse.from(service.create(request.nickname(), request.role())));
  }

  @Operation(
      summary = "데모 로그인",
      description = "인증 토큰 없이 닉네임으로 회원을 조회합니다. 응답의 memberId와 role을 이후 API에 사용합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 닉네임")
      })
  @PostMapping("/login")
  ApiResponse<MemberResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.of("LOGIN_SUCCESS", MemberResponse.from(service.login(request.nickname())));
  }

  @Schema(description = "회원가입 요청")
  public record CreateMemberRequest(
      @Schema(description = "중복되지 않는 닉네임", example = "야간알바") @NotBlank @Size(max = 30)
          String nickname,
      @Schema(
              description = "서비스 역할",
              example = "WORKER",
              allowableValues = {"MANAGER", "WORKER"})
          @NotNull
          MemberRole role) {}

  @Schema(description = "데모 로그인 요청")
  public record LoginRequest(
      @Schema(description = "가입할 때 사용한 닉네임", example = "야간알바") @NotBlank String nickname) {}

  @Schema(description = "회원 식별 정보")
  public record MemberResponse(
      @Schema(description = "회원 ID", example = "2") Long memberId,
      @Schema(description = "닉네임", example = "야간알바") String nickname,
      @Schema(description = "역할", example = "WORKER") MemberRole role) {
    static MemberResponse from(Member member) {
      return new MemberResponse(member.getId(), member.getNickname(), member.getRole());
    }
  }
}
