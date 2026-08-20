package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.assignment.AssignmentService;
import com.ktb.hackathon.team11.assignment.AssignmentStatus;
import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "3. 업무 조회", description = "체크리스트 수행 상태 변경 API")
public class TaskChecklistController {
  private final AssignmentService service;

  @Operation(
      summary = "체크리스트 수행 여부 변경",
      description = "담당 WORKER가 CHECK 방식 체크리스트의 완료 여부를 변경합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "체크리스트 수행 여부 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "PHOTO 체크리스트 또는 수행 가능 시간 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "담당자 또는 그룹 구성원 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "태스크 또는 체크리스트를 찾을 수 없음")
      })
  @PatchMapping("/tasks/{taskId}/sub-tasks/{subTaskId}")
  ApiResponse<ChecklistUpdateResponse> update(
      @Parameter(description = "태스크 ID", example = "930001") @PathVariable long taskId,
      @Parameter(description = "체크리스트 배정 ID", example = "940001") @PathVariable long subTaskId,
      @Valid @RequestBody UpdateRequest request) {
    return ApiResponse.of(
        "TASK_CHECKLIST_UPDATED",
        ChecklistUpdateResponse.from(
            service.updatePerformed(taskId, subTaskId, request.workerId(), request.performed())));
  }

  @Schema(description = "체크리스트 수행 여부 변경 요청")
  public record UpdateRequest(
      @Schema(description = "수행 WORKER ID", example = "2") @NotNull Long workerId,
      @Schema(description = "수행 완료 여부", example = "true") @NotNull Boolean performed) {}

  @Schema(description = "체크리스트 수행 여부 변경 결과")
  public record ChecklistUpdateResponse(
      Long taskId,
      Long checklistId,
      boolean performed,
      java.time.LocalDateTime performedAt,
      AssignmentStatus status) {
    static ChecklistUpdateResponse from(TaskAssignment assignment) {
      return new ChecklistUpdateResponse(
          assignment.getSchedule().getTaskTemplate().getId(),
          assignment.getId(),
          assignment.getStatus() == AssignmentStatus.COMPLETED,
          assignment.getCompletedAt(),
          assignment.getStatus());
    }
  }
}
