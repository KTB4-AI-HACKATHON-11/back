package com.ktb.hackathon.team11.review;

import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ManagerReviewController {
  private final ManagerReviewService service;
  private final SessionService sessions;

  @PostMapping("/assignments/{assignmentId}/manager-reviews")
  ApiResponse<ManagerReviewService.Response> request(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long assignmentId,
      @Valid @RequestBody RequestReviewRequest request) {
    sessions.require(token, request.workerId());
    return ApiResponse.of(
        "MANAGER_REVIEW_REQUESTED",
        service.request(assignmentId, request.workerId()));
  }

  @GetMapping("/groups/{groupId}/manager-reviews")
  ApiResponse<List<ManagerReviewService.Response>> list(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @RequestParam long managerId,
      @RequestParam(required = false) ManagerReviewStatus status) {
    sessions.require(token, managerId);
    return ApiResponse.of("MANAGER_REVIEWS_FOUND", service.list(groupId, managerId, status));
  }

  @PatchMapping("/manager-reviews/{reviewId}")
  ApiResponse<ManagerReviewService.Response> resolve(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long reviewId,
      @Valid @RequestBody ResolveReviewRequest request) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "MANAGER_REVIEW_RESOLVED",
        service.resolve(
            reviewId, request.managerId(), request.decision(), request.message()));
  }

  public record RequestReviewRequest(@NotNull Long workerId) {}

  public record ResolveReviewRequest(
      @NotNull Long managerId,
      @NotNull ManagerReviewDecision decision,
      @Size(max = 500) String message) {}
}
