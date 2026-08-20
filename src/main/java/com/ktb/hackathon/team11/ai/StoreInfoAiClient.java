package com.ktb.hackathon.team11.ai;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "ai.stub-enabled", havingValue = "false", matchIfMissing = true)
public class StoreInfoAiClient implements StoreInfoAnswerClient {
  private final RestClient client;

  public StoreInfoAiClient(
      RestClient.Builder builder,
      @Value("${ai.base-url}") String baseUrl,
      @Value("${ai.service-token}") String token,
      @Value("${ai.connect-timeout-seconds:5}") long connectTimeout,
      @Value("${ai.read-timeout-seconds:60}") long readTimeout) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeout)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));
    client = builder.requestFactory(requestFactory).baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer " + token).build();
  }

  public String answer(String question, String information) {
    BusinessException last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        AnswerResponse response = client.post().uri("/v1/knowledge/answer")
            .body(new AnswerRequest(question, information)).retrieve()
            .onStatus(HttpStatusCode::isError, (request, responseError) -> {
              if (responseError.getStatusCode().value() == 503) throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
              throw new BusinessException(ErrorCode.AI_INVALID_REQUEST);
            }).body(AnswerResponse.class);
        if (response == null || response.answer() == null) throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        return response.answer();
      } catch (BusinessException exception) {
        last = exception;
        if (exception.getErrorCode() != ErrorCode.AI_UNAVAILABLE) throw exception;
      } catch (RestClientException exception) {
        log.warn("Store information AI request failed, attempt={}", attempt + 1);
        last = new BusinessException(ErrorCode.AI_UNAVAILABLE);
      }
    }
    throw last == null ? new BusinessException(ErrorCode.AI_UNAVAILABLE) : last;
  }

  private record AnswerRequest(String question, String information) {}
  private record AnswerResponse(String answer) {}
}
