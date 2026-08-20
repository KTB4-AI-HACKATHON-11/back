package com.ktb.hackathon.team11.agent;

import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/agent")
@RequiredArgsConstructor
public class GroupAgentController {
  private final GroupAgentService service;
  private final SessionService sessions;

  @GetMapping("/turns")
  ApiResponse<List<GroupAgentService.TurnResponse>> history(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @RequestParam long managerId,
      @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
    sessions.require(token, managerId);
    return ApiResponse.of("GROUP_AGENT_HISTORY_FOUND", service.history(groupId, managerId, limit));
  }

  @GetMapping("/turns/{requestId}")
  ApiResponse<GroupAgentService.TurnResponse> turn(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @PathVariable
          @Pattern(
              regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
              message = "요청 ID 형식이 올바르지 않습니다.")
          String requestId,
      @RequestParam long managerId) {
    sessions.require(token, managerId);
    return ApiResponse.of("GROUP_AGENT_TURN_FOUND", service.turn(groupId, managerId, requestId));
  }

  @PostMapping("/messages")
  ApiResponse<GroupAgentService.ChatResponse> chat(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @Valid @RequestBody ChatRequest request) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "GROUP_AGENT_RESPONDED",
        service.chat(groupId, request.managerId(), request.requestId(), request.message()));
  }

  public record ChatRequest(
      @NotNull Long managerId,
      @NotBlank
          @Pattern(
              regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
              message = "요청 ID 형식이 올바르지 않습니다.")
          String requestId,
      @NotBlank @Size(max = 2_000) String message) {}
}
