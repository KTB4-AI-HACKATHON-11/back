package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import com.ktb.hackathon.team11.member.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushNotificationController {
  private final SessionService sessions;
  private final PushSubscriptionService subscriptions;
  private final WebPushSender sender;

  @GetMapping("/public-key")
  ApiResponse<PublicKeyResponse> publicKey() {
    return ApiResponse.of(
        "PUSH_PUBLIC_KEY_FOUND", new PublicKeyResponse(sender.isEnabled(), sender.publicKey()));
  }

  @PutMapping("/subscriptions")
  ApiResponse<SubscriptionResponse> subscribe(
      @CookieValue(value = SessionService.COOKIE_NAME, required = false) String token,
      @Valid @RequestBody SubscriptionRequest request) {
    Member member = sessions.require(token);
    PushSubscriptionRecord saved =
        subscriptions.upsert(
            member,
            request.endpoint(),
            request.keys().p256dh(),
            request.keys().auth(),
            request.expirationTime());
    return ApiResponse.of(
        "PUSH_SUBSCRIPTION_SAVED",
        new SubscriptionResponse(saved.getId(), saved.getEndpointHash(), saved.isActive()));
  }

  @DeleteMapping("/subscriptions/{endpointHash}")
  ApiResponse<Void> unsubscribe(
      @CookieValue(value = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable @Pattern(regexp = "[0-9a-f]{64}") String endpointHash) {
    subscriptions.deactivate(sessions.require(token), endpointHash);
    return ApiResponse.of("PUSH_SUBSCRIPTION_DISABLED", null);
  }

  public record PublicKeyResponse(boolean enabled, String publicKey) {}

  public record SubscriptionRequest(
      @NotBlank @Size(max = 4096) String endpoint,
      Long expirationTime,
      @NotNull @Valid Keys keys) {}

  public record Keys(
      @NotBlank @Size(max = 256) String p256dh,
      @NotBlank @Size(max = 128) String auth) {}

  public record SubscriptionResponse(Long subscriptionId, String endpointHash, boolean active) {}
}
