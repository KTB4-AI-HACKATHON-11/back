package com.ktb.hackathon.team11.attempt;

import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "6. 사진 인증", description = "PHOTO 업무 제출, AI 판정 결과, 재촬영과 검사 지연 처리 API")
public class TaskAttemptController {
  private final TaskAttemptService service;

  @Operation(
      summary = "PHOTO 업무 인증 사진 제출",
      description =
          "WORKER가 JPEG, PNG 또는 WebP 사진을 제출합니다. 서버가 10MB 제한, SHA-256 중복 여부를 확인하고 S3 저장 후 AI에 검사를"
              + " 요청합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "AI PASS 또는 RETAKE 판정 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "지원하지 않는 이미지 또는 CHECK 업무"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "중복 사진, 수행 시간 외 요청 또는 완료된 업무"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "502",
            description = "AI 요청 형식 또는 서비스 인증 설정 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "스토리지 사용 불가")
      })
  @PostMapping(value = "/assignments/{id}/photo-attempts", consumes = "multipart/form-data")
  ApiResponse<Response> submit(
      @Parameter(description = "PHOTO 배정 업무 ID", example = "15") @PathVariable long id,
      @Parameter(description = "사진을 제출한 WORKER ID", example = "2") @RequestParam long workerId,
      @Parameter(description = "인증 사진(JPEG/PNG/WebP, 1바이트~10MB)") @RequestPart
          MultipartFile photo) {
    return ApiResponse.of("PHOTO_CHECKED", Response.from(service.submit(id, workerId, photo)));
  }

  @Operation(
      summary = "사진 제출 이력 조회",
      description = "그룹 구성원이 특정 배정 업무의 모든 제출 차수와 PASS, RETAKE, DELAYED 결과를 조회합니다.")
  @GetMapping("/assignments/{id}/attempts")
  ApiResponse<List<Response>> history(@PathVariable long id, @RequestParam long memberId) {
    return ApiResponse.of(
        "ATTEMPTS_FOUND", service.history(id, memberId).stream().map(Response::from).toList());
  }

  @Operation(
      summary = "검사 지연 제출 재처리",
      description = "MANAGER가 DELAYED 상태의 제출에 새 presigned URL을 발급해 AI 검사를 다시 요청합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "재검사 처리 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "그룹 관리자 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "DELAYED 상태가 아닌 제출")
      })
  @PostMapping("/attempts/{id}/retry")
  ApiResponse<Response> retry(@PathVariable long id, @RequestParam long managerId) {
    return ApiResponse.of("ATTEMPT_RETRIED", Response.from(service.retry(id, managerId)));
  }

  @Schema(description = "사진 제출 및 AI 판정 결과")
  public record Response(
      @Schema(description = "제출 이력 ID", example = "41") Long attemptId,
      @Schema(description = "해당 업무의 제출 차수", example = "2") int attemptNumber,
      @Schema(
              description = "AI 검사 상태",
              example = "RETAKE",
              allowableValues = {"VERIFYING", "PASS", "RETAKE", "DELAYED"})
          AttemptStatus status,
      @Schema(description = "업무 전체 상태", example = "RETAKE_REQUIRED")
          AssignmentStatus assignmentStatus,
      @Schema(description = "AI 판정 사유", example = "POS 화면이 사진에서 보이지 않습니다.") String reason,
      @Schema(
              description = "RETAKE일 때 재촬영 안내",
              example = "POS 화면이 선명하게 보이도록 다시 촬영해 주세요.",
              nullable = true)
          String fix) {
    static Response from(TaskAttemptService.Result result) {
      return new Response(
          result.attemptId(),
          result.attemptNumber(),
          result.status(),
          result.assignmentStatus(),
          result.reason(),
          result.fix());
    }
  }
}
