package com.ktb.hackathon.team11.ai;

import com.ktb.hackathon.team11.global.exception.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
@ConditionalOnProperty(name = "ai.stub-enabled", havingValue = "false")
public class HttpAiTaskClient implements AiTaskClient {
  private final RestClient client;

  public HttpAiTaskClient(
      RestClient.Builder builder,
      @Value("${ai.base-url}") String baseUrl,
      @Value("${ai.service-token}") String token,
      @Value("${ai.connect-timeout-seconds:5}") long connectTimeout,
      @Value("${ai.read-timeout-seconds:60}") long readTimeout) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeout)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));
    this.client =
        builder
            .requestFactory(requestFactory)
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
  }

  @Override
  public List<GeneratedTask> generateTasks(String message) {
    GenerateResponse response =
        exchangeWithRetry(
            "/v1/tasks/generate", new GenerateRequest(message), GenerateResponse.class);
    if (response == null
        || response.tasks() == null
        || response.tasks().isEmpty()
        || response.tasks().size() > 20) throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    return response.tasks().stream()
        .map(t -> new GeneratedTask(t.title(), t.instruction(), t.completionType(), t.rule()))
        .toList();
  }

  @Override
  public PhotoCheckResult checkPhoto(PhotoCheckCommand c) {
    CheckRequest request =
        new CheckRequest(
            new TaskPayload(c.title(), c.instruction(), c.rule()),
            new PhotoPayload(c.mimeType(), c.sizeBytes(), c.sha256(), c.url()));
    CheckResponse r = exchangeWithRetry("/v1/attempts/check", request, CheckResponse.class);
    if (r == null || r.status() == null || r.reason() == null)
      throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    return new PhotoCheckResult(r.status(), r.reason(), r.fix());
  }

  private <T> T exchangeWithRetry(String path, Object body, Class<T> type) {
    BusinessException last = null;
    for (int i = 0; i < 2; i++)
      try {
        return client
            .post()
            .uri(path)
            .body(body)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (req, res) -> {
                  int status = res.getStatusCode().value();
                  if (status == 400) throw new BusinessException(ErrorCode.AI_INVALID_REQUEST);
                  if (status == 401) throw new BusinessException(ErrorCode.AI_UNAUTHORIZED);
                  if (status == 422) throw new BusinessException(ErrorCode.PHOTO_UNAVAILABLE);
                  throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
                })
            .body(type);
      } catch (BusinessException e) {
        last = e;
        if (e.getErrorCode() != ErrorCode.AI_UNAVAILABLE) throw e;
      } catch (RestClientException e) {
        last = new BusinessException(ErrorCode.AI_UNAVAILABLE);
      }
    throw last == null ? new BusinessException(ErrorCode.AI_UNAVAILABLE) : last;
  }

  record GenerateRequest(String message) {}

  record GenerateResponse(List<TaskPayloadResponse> tasks) {}

  record TaskPayloadResponse(
      String title, String instruction, CompletionType completionType, String rule) {}

  record CheckRequest(TaskPayload task, PhotoPayload photo) {}

  record TaskPayload(String title, String instruction, String rule) {}

  record PhotoPayload(String mimeType, long sizeBytes, String sha256, String url) {}

  record CheckResponse(PhotoCheckStatus status, String reason, String fix) {}
}
