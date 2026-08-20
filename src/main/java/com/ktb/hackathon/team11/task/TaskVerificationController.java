package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "3. 업무 조회", description = "태스크 검증 기준 저장 API")
public class TaskVerificationController {
  private final TaskVerificationService service;
  private final SessionService sessions;

  @Operation(
      summary = "태스크 검증 기준 일괄 저장",
      description = "관리자가 담당자, 마감 일시, 체크리스트 완료 방식과 기준 사진을 한 번에 수정합니다.")
  @PatchMapping(value = "/tasks/{taskId}/verification-settings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ApiResponse<TaskVerificationService.UpdatedSettings> update(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Parameter(description = "태스크 ID", example = "930001") @PathVariable long taskId,
      @Valid @RequestPart("request") UpdateRequest request,
      @RequestPart(value = "referencePhotos", required = false) List<MultipartFile> referencePhotos) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "TASK_VERIFICATION_SETTINGS_UPDATED",
        "검증 기준이 저장되었습니다.",
        service.update(
            taskId,
            request.managerId(),
            request.workerId(),
            request.dueAt(),
            request.items().stream()
                .map(
                    item ->
                        new TaskVerificationService.ItemCommand(
                            item.checklistId(),
                            item.enabled(),
                            item.completionType(),
                            item.rule(),
                            item.referencePhotoIndex()))
                .toList(),
            referencePhotos == null ? List.of() : referencePhotos));
  }

  @Schema(description = "태스크 검증 기준 저장 요청")
  public record UpdateRequest(
      @NotNull Long managerId,
      @NotNull Long workerId,
      @NotNull OffsetDateTime dueAt,
      @NotEmpty @Size(max = 20) List<@Valid ItemRequest> items) {}

  @Schema(description = "체크리스트 검증 기준 저장 요청")
  public record ItemRequest(
      @NotNull Long checklistId,
      @NotNull Boolean enabled,
      @NotNull CompletionType completionType,
      @Size(max = 1000) String rule,
      @PositiveOrZero Integer referencePhotoIndex) {}
}
