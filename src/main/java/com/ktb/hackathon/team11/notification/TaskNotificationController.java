package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskNotificationController {
  private final SessionService sessions;
  private final TaskNotificationPreferenceService preferences;

  @PatchMapping("/{taskId}/completion-notification")
  ApiResponse<PreferenceResponse> update(
      @CookieValue(value = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long taskId,
      @Valid @RequestBody PreferenceRequest request) {
    boolean enabled = preferences.update(taskId, sessions.require(token), request.enabled());
    return ApiResponse.of(
        "TASK_COMPLETION_NOTIFICATION_UPDATED", new PreferenceResponse(taskId, enabled));
  }

  public record PreferenceRequest(@NotNull Boolean enabled) {}

  public record PreferenceResponse(long taskId, boolean enabled) {}
}
