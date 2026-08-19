package com.ktb.hackathon.team11.assignment;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "5. 업무 배정", description = "날짜별 업무 조회와 CHECK 방식 완료 API")
public class AssignmentController {
  private final AssignmentService service;

  @Operation(
      summary = "알바생 날짜별 업무 조회",
      description = "WORKER에게 직접 배정된 업무와 소속 그룹의 담당자 미지정 업무를 함께 조회합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "업무 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "WORKER 역할 또는 그룹 소속 오류")
      })
  @GetMapping("/workers/{workerId}/assignments")
  ApiResponse<List<Response>> worker(
      @Parameter(description = "WORKER 회원 ID", example = "2") @PathVariable long workerId,
      @Parameter(description = "조회할 업무 기준일", example = "2026-08-20") @RequestParam LocalDate date) {
    return ApiResponse.of(
        "ASSIGNMENTS_FOUND", service.worker(workerId, date).stream().map(Response::from).toList());
  }

  @Operation(summary = "관리자 그룹 업무 현황", description = "MANAGER가 지정 날짜의 그룹 전체 업무와 수행 상태를 조회합니다.")
  @GetMapping("/groups/{groupId}/assignments")
  ApiResponse<List<Response>> group(
      @PathVariable long groupId, @RequestParam long managerId, @RequestParam LocalDate date) {
    return ApiResponse.of(
        "ASSIGNMENTS_FOUND",
        service.group(groupId, managerId, date).stream().map(Response::from).toList());
  }

  @Operation(
      summary = "배정 업무 상세 조회",
      description = "업무 제목, 수행 안내, 완료 방식, 담당자, 수행 가능 시간과 현재 상태를 조회합니다.")
  @GetMapping("/assignments/{id}")
  ApiResponse<Response> detail(@PathVariable long id, @RequestParam long memberId) {
    return ApiResponse.of("ASSIGNMENT_FOUND", Response.from(service.require(id)));
  }

  @Operation(
      summary = "CHECK 업무 완료",
      description = "WORKER가 사진 인증이 필요 없는 CHECK 업무를 수행 가능 시간 안에 직접 완료합니다. PHOTO 업무에는 사용할 수 없습니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "업무 완료 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "PHOTO 업무에 CHECK 완료 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "수행 가능 시간이 아니거나 이미 완료된 업무")
      })
  @PostMapping("/assignments/{id}/check")
  ApiResponse<Response> check(@PathVariable long id, @Valid @RequestBody CheckRequest request) {
    return ApiResponse.of(
        "ASSIGNMENT_COMPLETED", Response.from(service.check(id, request.workerId())));
  }

  @Schema(description = "CHECK 업무 완료 요청")
  public record CheckRequest(
      @Schema(description = "업무를 수행한 WORKER ID", example = "2") @NotNull Long workerId) {}

  @Schema(description = "날짜별 실제 업무 정보")
  public record Response(
      @Schema(description = "배정 업무 ID", example = "15") Long assignmentId,
      @Schema(description = "업무 제목", example = "POS 전원 확인") String title,
      @Schema(description = "알바생 수행 안내", example = "POS 화면이 보이도록 촬영해 주세요.") String instruction,
      @Schema(description = "완료 방식", example = "PHOTO") CompletionType completionType,
      @Schema(
              description = "현재 상태",
              example = "PENDING",
              allowableValues = {
                "PENDING",
                "VERIFYING",
                "RETAKE_REQUIRED",
                "COMPLETED",
                "VERIFICATION_DELAYED",
                "EXPIRED"
              })
          AssignmentStatus status,
      @Schema(description = "수행 가능 시작", example = "2026-08-20T22:50:00")
          LocalDateTime availableFrom,
      @Schema(description = "수행 가능 종료", example = "2026-08-21T06:20:00") LocalDateTime dueAt,
      @Schema(description = "담당 WORKER ID. null이면 그룹 공용", example = "2", nullable = true)
          Long assigneeId) {
    static Response from(TaskAssignment assignment) {
      return new Response(
          assignment.getId(),
          assignment.getTaskItemTemplate().getTitle(),
          assignment.getTaskItemTemplate().getInstruction(),
          assignment.getTaskItemTemplate().getCompletionType(),
          assignment.getStatus(),
          assignment.getAvailableFrom(),
          assignment.getDueAt(),
          assignment.getAssignee() == null ? null : assignment.getAssignee().getId());
    }
  }
}
