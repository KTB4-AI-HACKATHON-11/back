package com.ktb.hackathon.team11.ai;

import com.ktb.hackathon.team11.global.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "ai.stub-enabled", havingValue = "false", matchIfMissing = true)
public class HttpAiTaskClient implements AiTaskClient {
    private static final Logger log = LoggerFactory.getLogger(HttpAiTaskClient.class);
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public HttpAiTaskClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.service-token}") String token,
            @Value("${ai.connect-timeout-seconds:5}") long connectTimeout,
            @Value("${ai.read-timeout-seconds:60}") long readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));
        this.client = builder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GeneratedTask> generateTasks(String message) {
        GenerateResponse response = exchangeWithRetry("/v1/tasks/generate", new GenerateRequest(message), GenerateResponse.class);
        if (response == null || response.tasks() == null || response.tasks().isEmpty() || response.tasks().size() > 20) {
            log.warn("AI task generation returned an invalid task collection");
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        List<GeneratedTask> tasks = response.tasks().stream()
                .map(task -> new GeneratedTask(task.title(), task.instruction(), task.completionType(), task.rule()))
                .toList();
        tasks.forEach(this::validateGeneratedTask);
        return tasks;
    }

    @Override
    public PhotoCheckResult checkPhoto(PhotoCheckCommand command) {
        CheckRequest request = new CheckRequest(
                new TaskPayload(command.title(), command.instruction(), command.rule()),
                PhotoPayload.from(command.photo()),
                PhotoPayload.from(command.referencePhoto())
        );
        CheckResponse response = exchangeWithRetry(
                "/v1/attempts/check", request, CheckResponse.class, this::validateCheckResponse);
        return new PhotoCheckResult(response.status(), response.reason(), response.status() == PhotoCheckStatus.PASS ? null : response.fix());
    }

    @Override
    public String answerKnowledge(String information, String question) {
        if (information == null || information.isBlank() || information.length() > 60_000
                || question == null || question.isBlank() || question.length() > 200) {
            throw new BusinessException(ErrorCode.AI_INVALID_REQUEST);
        }
        KnowledgeResponse response = exchangeWithRetry(
                "/v1/knowledge/answer",
                new KnowledgeRequest(information, question),
                KnowledgeResponse.class);
        if (response == null || response.answer() == null || response.answer().isBlank()
                || response.answer().length() > 8_000) {
            log.warn("AI knowledge answer returned an invalid response shape");
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        return response.answer();
    }

    private void validateCheckResponse(CheckResponse response) {
        if (response == null || response.status() == null || response.reason() == null || response.reason().isBlank() || response.reason().length() > 500) {
            log.warn("AI photo check returned an invalid response shape");
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        if (response.status() == PhotoCheckStatus.RETAKE && (response.fix() == null || response.fix().isBlank() || response.fix().length() > 500)) {
            log.warn("AI photo check RETAKE response did not include a valid fix message");
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private void validateGeneratedTask(GeneratedTask task) {
        boolean invalidText = task.title() == null || task.title().isBlank() || task.title().length() > 80
                || task.instruction() == null || task.instruction().isBlank() || task.instruction().length() > 500;
        boolean invalidRule = task.completionType() == null
                || task.completionType() == CompletionType.PHOTO && (task.rule() == null || task.rule().isBlank() || task.rule().length() > 1000)
                || task.completionType() == CompletionType.CHECK && task.rule() != null;
        if (invalidText || invalidRule) throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    private <T> T exchangeWithRetry(String path, Object body, Class<T> responseType) {
        return exchangeWithRetry(path, body, responseType, ignored -> {});
    }

    private <T> T exchangeWithRetry(
            String path, Object body, Class<T> responseType, Consumer<T> validator) {
        BusinessException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                T response = client.post().uri(path).body(body).retrieve()
                        .onStatus(HttpStatusCode::isError, (request, errorResponse) -> handleError(errorResponse))
                        .body(responseType);
                validator.accept(response);
                return response;
            } catch (PhotoUnavailableException exception) {
                log.warn("AI backend could not read photo field={}", exception.getField());
                throw exception;
            } catch (BusinessException exception) {
                last = exception;
                log.warn("AI backend business failure path={} attempt={} code={}", path, attempt + 1, exception.getErrorCode());
                if (exception.getErrorCode() != ErrorCode.AI_UNAVAILABLE) throw exception;
            } catch (RestClientException exception) {
                log.warn("AI backend transport failure path={} attempt={} type={}", path, attempt + 1, exception.getClass().getSimpleName());
                last = new BusinessException(ErrorCode.AI_UNAVAILABLE);
            }
        }
        throw last == null ? new BusinessException(ErrorCode.AI_UNAVAILABLE) : last;
    }

    private void handleError(org.springframework.http.client.ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (status == 400) throw new BusinessException(ErrorCode.AI_INVALID_REQUEST);
        if (status == 401) throw new BusinessException(ErrorCode.AI_UNAUTHORIZED);
        if (status == 422) {
            ErrorEnvelope envelope = objectMapper.readValue(response.getBody(), ErrorEnvelope.class);
            throw new PhotoUnavailableException(envelope != null && envelope.error() != null ? envelope.error().field() : null);
        }
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    record GenerateRequest(String message) {}
    record GenerateResponse(List<TaskPayloadResponse> tasks) {}
    record TaskPayloadResponse(String title, String instruction, CompletionType completionType, String rule) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record CheckRequest(TaskPayload task, PhotoPayload photo, PhotoPayload referencePhoto) {}
    record TaskPayload(String title, String instruction, String rule) {}
    record PhotoPayload(String mimeType, long sizeBytes, String sha256, String url) {
        static PhotoPayload from(PhotoCheckCommand.PhotoResource resource) {
            return resource == null ? null : new PhotoPayload(resource.mimeType(), resource.sizeBytes(), resource.sha256(), resource.url());
        }
    }
    record CheckResponse(PhotoCheckStatus status, String reason, String fix) {}
    record KnowledgeRequest(String information, String question) {}
    record KnowledgeResponse(String answer) {}
    record ErrorEnvelope(ErrorPayload error) {}
    record ErrorPayload(String code, String message, String field) {}
}
