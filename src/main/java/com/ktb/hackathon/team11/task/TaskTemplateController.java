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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "3. 업무 템플릿", description = "AI 업무 초안과 재사용 가능한 업무 템플릿 관리 API")
public class TaskTemplateController {
  private final TaskTemplateService service;
  private final TaskRegistrationService registrationService;
  private final SessionService sessions;

  @Operation(
      summary = "AI 체크리스트 생성",
      description =
          "MANAGER의 자연어를 AI 서버에 전달해 PHOTO 또는 CHECK 체크리스트로 변환합니다. 결과는 저장하지 않고 프런트엔드에 반환합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "AI 체크리스트 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "그룹 관리자 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422",
            description = "구체적인 업무를 찾을 수 없어 AI가 생성을 거부함"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "AI 서버 처리 실패")
      })
  @PostMapping("/groups/{groupId}/tasks/generate")
  ApiResponse<TaskTemplateService.GeneratedTasksResponse> generate(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Parameter(description = "업무를 등록할 그룹 ID", example = "1") @PathVariable long groupId,
      @Valid @RequestBody GenerateTasksRequest request) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "TASKS_GENERATED",
        service.generateTasks(
            groupId, request.managerId(), request.title(), request.message()));
  }

  @Operation(
      summary = "태스크 최종 등록",
      description =
          "그룹 WORKER와 마감 일시를 지정하고 체크리스트를 저장합니다. PHOTO 항목에는 기준 사진이 반드시 필요합니다.")
  @PostMapping(value = "/groups/{groupId}/tasks", consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<TaskRegistrationService.TaskCreatedResponse> createTask(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @Valid @RequestPart("request") CreateTaskRequest request,
      @RequestPart(value = "referencePhotos", required = false)
          List<MultipartFile> referencePhotos) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "TASK_CREATED",
        registrationService.create(
            groupId,
            request.managerId(),
            request.title(),
            request.message(),
            request.workerId(),
            request.dueAt(),
            request.checklists().stream()
                .map(
                    checklist ->
                        new TaskRegistrationService.ChecklistCommand(
                            checklist.sequence(),
                            checklist.title(),
                            checklist.instruction(),
                            checklist.completionType(),
                            checklist.rule(),
                            checklist.referencePhotoIndex()))
                .toList(),
            referencePhotos == null ? List.of() : referencePhotos,
            Boolean.TRUE.equals(request.notifyOnCompletion())));
  }

  @Operation(
      summary = "업무 템플릿 등록",
      description =
          "관리자가 AI 초안을 확인·수정한 뒤 하나의 템플릿과 순서가 있는 하위 업무로 저장합니다. PHOTO는 verificationRule이 필수이고 CHECK는"
              + " null이어야 합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "템플릿 등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "PHOTO/CHECK 검증 규칙 또는 입력 형식 오류")
      })
  @PostMapping("/groups/{groupId}/task-templates")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<TemplateResponse> create(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @Valid @RequestBody CreateTemplateRequest request) {
    sessions.require(token, request.managerId());
    TaskTemplate template =
        service.create(
            groupId,
            request.managerId(),
            request.title(),
            request.sourceMessage(),
            request.items().stream()
                .map(
                    item ->
                        new TaskTemplateService.ItemCommand(
                            item.title(),
                            item.instruction(),
                            item.completionType(),
                            item.verificationRule()))
                .toList());
    return ApiResponse.of(
        "TASK_TEMPLATE_CREATED", TemplateResponse.from(template, service.items(template.getId())));
  }

  @Operation(summary = "그룹 업무 템플릿 목록", description = "그룹 구성원이 현재 활성화된 업무 템플릿과 하위 업무를 조회합니다.")
  @GetMapping("/groups/{groupId}/task-templates")
  ApiResponse<List<TemplateResponse>> list(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @RequestParam long memberId) {
    sessions.require(token, memberId);
    return ApiResponse.of(
        "TASK_TEMPLATES_FOUND",
        service.list(groupId, memberId).stream()
            .map(template -> TemplateResponse.from(template, service.items(template.getId())))
            .toList());
  }

  @Operation(summary = "업무 템플릿 상세", description = "그룹 구성원이 템플릿 하나와 PHOTO/CHECK 하위 업무 전체를 조회합니다.")
  @GetMapping("/task-templates/{id}")
  ApiResponse<TemplateResponse> detail(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long id,
      @RequestParam long memberId) {
    sessions.require(token, memberId);
    TaskTemplate template = service.require(id);
    service.list(template.getGroup().getId(), memberId);
    return ApiResponse.of(
        "TASK_TEMPLATE_FOUND", TemplateResponse.from(template, service.items(id)));
  }

  @Operation(summary = "업무 템플릿 수정", description = "MANAGER가 템플릿 제목 또는 활성 상태를 변경합니다.")
  @PatchMapping("/task-templates/{id}")
  ApiResponse<TemplateResponse> update(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long id,
      @Valid @RequestBody UpdateTemplateRequest request) {
    sessions.require(token, request.managerId());
    TaskTemplate template =
        service.update(id, request.managerId(), request.title(), request.active());
    return ApiResponse.of(
        "TASK_TEMPLATE_UPDATED", TemplateResponse.from(template, service.items(id)));
  }

  @Operation(
      summary = "PHOTO 기준 사진 등록",
      description = "PHOTO 하위 업무에 관리자가 참고할 기준 사진을 저장합니다. JPEG, PNG, WebP만 가능하고 최대 10MB입니다.")
  @PostMapping(value = "/task-items/{itemId}/reference-image", consumes = "multipart/form-data")
  ApiResponse<String> reference(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Parameter(description = "PHOTO 하위 업무 ID", example = "10") @PathVariable long itemId,
      @Parameter(description = "요청 관리자 ID", example = "1") @RequestParam long managerId,
      @Parameter(description = "기준 이미지 파일(JPEG/PNG/WebP, 최대 10MB)") @RequestPart
          MultipartFile photo) {
    sessions.require(token, managerId);
    return ApiResponse.of(
        "REFERENCE_IMAGE_SAVED", service.addReferenceImage(itemId, managerId, photo));
  }

  @Operation(summary = "업무 템플릿 비활성화", description = "기존 수행 이력은 보존하고 이후 배정 생성을 막습니다.")
  @DeleteMapping("/task-templates/{id}")
  ApiResponse<Void> delete(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long id,
      @RequestParam long managerId) {
    sessions.require(token, managerId);
    service.deactivate(id, managerId);
    return ApiResponse.of("TASK_TEMPLATE_DEACTIVATED", null);
  }

  @Schema(description = "AI 체크리스트 생성 요청")
  public record GenerateTasksRequest(
      @Schema(description = "요청 MANAGER ID", example = "1") @NotNull Long managerId,
      @Schema(description = "태스크 제목", example = "오픈 전 매장 점검") @NotBlank @Size(max = 80)
          String title,
      @Schema(
              description = "AI가 체크리스트로 분리할 자연어",
              example = "오픈 전에 조명을 켜고 POS기 전원과 카운터 정리를 확인해야 해",
              maxLength = 2000)
          @NotBlank
          @Size(max = 2000)
          String message) {}

  @Schema(description = "태스크 최종 등록 요청")
  public record CreateTaskRequest(
      @NotNull Long managerId,
      @NotBlank @Size(max = 80) String title,
      @NotBlank @Size(max = 2000) String message,
      @NotNull Long workerId,
      @NotNull @Future OffsetDateTime dueAt,
      Boolean notifyOnCompletion,
      @NotEmpty @Size(max = 20) List<@Valid ChecklistRequest> checklists) {}

  @Schema(description = "최종 등록할 체크리스트")
  public record ChecklistRequest(
      @Min(1) int sequence,
      @NotBlank @Size(max = 80) String title,
      @NotBlank @Size(max = 500) String instruction,
      @NotNull CompletionType completionType,
      @Size(max = 1000) String rule,
      @PositiveOrZero Integer referencePhotoIndex) {}

  @Schema(description = "하위 업무 등록 정보")
  public record ItemRequest(
      @Schema(description = "업무 제목", example = "POS 전원 확인") @NotBlank @Size(max = 80) String title,
      @Schema(description = "알바생 수행 안내", example = "POS 화면이 보이도록 촬영해 주세요.")
          @NotBlank
          @Size(max = 500)
          String instruction,
      @Schema(
              description = "완료 방식",
              example = "PHOTO",
              allowableValues = {"PHOTO", "CHECK"})
          @NotNull
          CompletionType completionType,
      @Schema(
              description = "PHOTO 판정 조건. CHECK일 때는 null",
              example = "POS 화면이 켜져 있고 정상 화면이 표시되어야 한다.")
          @Size(max = 1000)
          String verificationRule) {}

  @Schema(description = "업무 템플릿 등록 요청")
  public record CreateTemplateRequest(
      @Schema(example = "1") @NotNull Long managerId,
      @Schema(example = "야간 POS 및 매장 정리") @NotBlank @Size(max = 80) String title,
      @Schema(example = "밤 11시에 POS 전원을 확인하고 매장 바닥을 청소해줘") @NotBlank @Size(max = 2000)
          String sourceMessage,
      @NotEmpty @Size(max = 20) List<@Valid ItemRequest> items) {}

  @Schema(description = "업무 템플릿 부분 수정 요청")
  public record UpdateTemplateRequest(
      @Schema(example = "1") @NotNull Long managerId,
      @Schema(example = "수정된 야간 업무") @Size(max = 80) String title,
      @Schema(example = "true") Boolean active) {}

  @Schema(description = "하위 업무 정보")
  public record ItemResponse(
      Long itemId,
      int sequence,
      String title,
      String instruction,
      CompletionType completionType,
      String verificationRule) {
    static ItemResponse from(TaskItemTemplate item) {
      return new ItemResponse(
          item.getId(),
          item.getSequence(),
          item.getTitle(),
          item.getInstruction(),
          item.getCompletionType(),
          item.getVerificationRule());
    }
  }

  @Schema(description = "업무 템플릿 상세 응답")
  public record TemplateResponse(
      Long templateId, Long groupId, String title, boolean active, List<ItemResponse> items) {
    static TemplateResponse from(TaskTemplate template, List<TaskItemTemplate> items) {
      return new TemplateResponse(
          template.getId(),
          template.getGroup().getId(),
          template.getTitle(),
          template.isActive(),
          items.stream().map(ItemResponse::from).toList());
    }
  }
}
