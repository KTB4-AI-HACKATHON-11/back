package com.ktb.hackathon.team11.knowledge;

import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Tag(name = "7. 매장 정보 질문", description = "매장 운영 정보를 근거로 직원 질문에 답변하는 API")
public class KnowledgeController {

  private final KnowledgeService service;

  @PostMapping("/answer")
  @Operation(summary = "매장 정보 질문", description = "등록된 매장 정보만 근거로 AI 답변을 생성합니다.")
  ApiResponse<KnowledgeService.AnswerResponse> answer(
      @Valid @RequestBody AnswerRequest request) {
    return ApiResponse.of(
        "KNOWLEDGE_ANSWERED",
        service.answer(
            request.groupId(),
            request.requesterId(),
            request.conversationId(),
            request.question()));
  }

  @Schema(description = "매장 정보 질문 요청")
  public record AnswerRequest(
      @Schema(description = "매장 정보가 속한 그룹 ID", example = "1")
          @jakarta.validation.constraints.NotNull
          Long groupId,
      @Schema(description = "질문하는 그룹 구성원 ID", example = "2")
          @jakarta.validation.constraints.NotNull
          Long requesterId,
      @Schema(description = "이전 응답에서 받은 대화 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
          @Size(max = 100, message = "대화 ID는 100자를 초과할 수 없습니다.")
          String conversationId,
      @Schema(example = "일반 택배는 몇 시까지 받고 냉장 택배도 접수할 수 있어?")
          @NotBlank(message = "질문은 필수입니다.")
          @Size(max = 200, message = "질문은 200자를 초과할 수 없습니다.")
          String question) {}
}
