package com.ktb.hackathon.team11.notification;

import java.nio.charset.StandardCharsets;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebPushSender {
  private final boolean enabled;
  private final String publicKey;
  private final String privateKey;
  private final String subject;

  public WebPushSender(
      @Value("${web-push.enabled:false}") boolean enabled,
      @Value("${web-push.public-key:}") String publicKey,
      @Value("${web-push.private-key:}") String privateKey,
      @Value("${web-push.subject:https://checkon.cloud}") String subject) {
    this.enabled = enabled;
    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.subject = subject;
    if (enabled && (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()))
      throw new IllegalStateException("Web Push VAPID 설정이 필요합니다.");
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String publicKey() {
    return publicKey;
  }

  public DeliveryResult send(PushSubscriptionRecord subscription, String payload) {
    if (!enabled) return new DeliveryResult(503, "WEB_PUSH_DISABLED", "");
    try {
      PushService pushService = new PushService(publicKey, privateKey, subject);
      Notification notification =
          new Notification(
              subscription.getEndpoint(),
              subscription.getP256dh(),
              subscription.getAuth(),
              payload.getBytes(StandardCharsets.UTF_8));
      HttpResponse response = pushService.send(notification);
      String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
      String requestId =
          response.getFirstHeader("apns-id") == null
              ? ""
              : response.getFirstHeader("apns-id").getValue();
      return new DeliveryResult(response.getStatusLine().getStatusCode(), body, requestId);
    } catch (Exception exception) {
      return new DeliveryResult(0, exception.getClass().getSimpleName(), "");
    }
  }

  public record DeliveryResult(int statusCode, String reason, String requestId) {
    public boolean successful() {
      return statusCode >= 200 && statusCode < 300;
    }

    public boolean expired() {
      return statusCode == 404 || statusCode == 410;
    }
  }
}
