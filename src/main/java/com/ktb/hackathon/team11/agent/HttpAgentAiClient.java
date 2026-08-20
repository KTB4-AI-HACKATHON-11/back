package com.ktb.hackathon.team11.agent;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "ai.stub-enabled", havingValue = "false", matchIfMissing = true)
public class HttpAgentAiClient implements AgentAiClient {
  private final RestClient client;

  public HttpAgentAiClient(
      RestClient.Builder builder,
      @Value("${ai.base-url}") String baseUrl,
      @Value("${ai.service-token}") String token,
      @Value("${ai.connect-timeout-seconds:5}") long connectTimeout,
      @Value("${ai.agent-read-timeout-seconds:90}") long readTimeout) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeout)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));
    client =
        builder
            .requestFactory(requestFactory)
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
  }

  @Override
  public Response respond(Request request) {
    try {
      Response response =
          client
              .post()
              .uri("/v1/agent/respond")
              .body(request)
              .retrieve()
              .onStatus(HttpStatusCode::isError, (ignored, error) -> handle(error.getStatusCode().value()))
              .body(Response.class);
      if (response == null
          || response.message() == null
          || response.message().length() > 4_000
          || response.toolCalls() == null
          || response.toolCalls().size() > 5
          || (response.toolCalls().isEmpty() && response.message().isBlank())) {
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
      }
      return response;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }
  }

  private void handle(int status) {
    if (status == 400) throw new BusinessException(ErrorCode.AI_INVALID_REQUEST);
    if (status == 401) throw new BusinessException(ErrorCode.AI_UNAUTHORIZED);
    throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
  }
}
