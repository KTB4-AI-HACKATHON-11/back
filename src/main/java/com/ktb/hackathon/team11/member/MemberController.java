package com.ktb.hackathon.team11.member;

import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "1. 회원", description = "데모 회원가입과 닉네임 로그인 API")
public class MemberController {
  private final MemberService service;
  private final SessionService sessions;

  @Operation(
      summary = "회원가입",
      description = "닉네임만 등록하며 기본 역할은 MANAGER입니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "닉네임 형식 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 사용 중인 닉네임")
      })
  @PostMapping
  ResponseEntity<ApiResponse<MemberResponse>> create(@Valid @RequestBody CreateMemberRequest request) {
    return authenticated(
        service.create(request.nickname()), "MEMBER_CREATED", HttpStatus.CREATED);
  }

  @Operation(
      summary = "데모 로그인",
      description = "닉네임으로 회원을 조회하고 이후 요청에 사용할 HttpOnly 세션 쿠키를 발급합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 닉네임")
      })
  @PostMapping("/login")
  ResponseEntity<ApiResponse<MemberResponse>> login(@Valid @RequestBody LoginRequest request) {
    return authenticated(service.login(request.nickname()), "LOGIN_SUCCESS", HttpStatus.OK);
  }

  @GetMapping("/me")
  ApiResponse<MemberResponse> me(
      @CookieValue(value = SessionService.COOKIE_NAME, required = false) String token) {
    return ApiResponse.of("SESSION_FOUND", MemberResponse.from(sessions.require(token)));
  }

  @PostMapping("/logout")
  ResponseEntity<ApiResponse<Void>> logout(
      @CookieValue(value = SessionService.COOKIE_NAME, required = false) String token) {
    sessions.revoke(token);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, sessions.clearCookie().toString())
        .body(ApiResponse.of("LOGOUT_SUCCESS", null));
  }

  private ResponseEntity<ApiResponse<MemberResponse>> authenticated(
      Member member, String code, HttpStatus status) {
    SessionService.IssuedSession issued = sessions.issue(member);
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, sessions.cookie(issued.token()).toString())
        .body(ApiResponse.of(code, MemberResponse.from(member)));
  }

  @Schema(description = "회원가입 요청")
  public record CreateMemberRequest(
      @Schema(description = "중복되지 않는 닉네임", example = "야간알바") @NotBlank @Size(max = 30)
          String nickname) {}

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
