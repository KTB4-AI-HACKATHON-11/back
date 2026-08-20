package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "3. 업무 조회", description = "그룹 태스크 목록과 상세 조회 API")
public class TaskQueryController {
  private final TaskQueryService service;

  @Operation(
      summary = "그룹 태스크 목록 조회",
      description = "그룹 구성원이 태스크를 상태, offset, limit 기준으로 조회합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "태스크 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "그룹 구성원 권한 없음")
      })
  @GetMapping("/groups/{groupId}/tasks")
  ApiResponse<TaskQueryService.TaskListResponse> list(
      @PathVariable long groupId,
      @RequestParam long requesterId,
      @RequestParam(defaultValue = "0") @PositiveOrZero int offset,
      @RequestParam(defaultValue = "20") @Positive int limit,
      @RequestParam(required = false) TaskStatus status) {
    return ApiResponse.of(
        "TASK_LIST_FOUND", service.list(groupId, requesterId, offset, limit, status));
  }

  @Operation(
      summary = "태스크 상세 조회",
      description = "그룹 구성원이 태스크의 진행 상태와 체크리스트를 조회합니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "태스크 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "그룹 구성원 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "태스크를 찾을 수 없음")
      })
  @GetMapping("/tasks/{taskId}")
  ApiResponse<TaskQueryService.TaskDetail> detail(
      @PathVariable long taskId,
      @Parameter(description = "조회 회원 ID", example = "2") @RequestParam long requesterId) {
    return ApiResponse.of("TASK_FOUND", service.detail(taskId, requesterId));
  }
}
