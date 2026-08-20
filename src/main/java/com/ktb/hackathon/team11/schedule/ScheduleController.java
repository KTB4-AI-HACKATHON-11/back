package com.ktb.hackathon.team11.schedule;

import com.ktb.hackathon.team11.auth.SessionService;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "4. 업무 일정", description = "담당자·근무 시간대·반복 규칙과 날짜별 업무 생성 API")
public class ScheduleController {
  private final ScheduleService service;
  private final SessionService sessions;

  @Operation(
      summary = "반복 일정 등록",
      description =
          "템플릿을 특정 알바생 또는 담당자 미지정 근무조에 배정합니다. endTime이 startTime보다 이르면 다음 날 종료되는 야간 일정입니다.",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "일정 등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "날짜, 시간, 반복 요일 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "관리자 권한 또는 그룹 소속 오류")
      })
  @PostMapping("/task-templates/{id}/schedules")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<Long> create(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @Parameter(description = "업무 템플릿 ID", example = "3") @PathVariable long id,
      @Valid @RequestBody ScheduleRequest request) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "SCHEDULE_CREATED",
        service
            .create(
                id,
                request.managerId(),
                request.assigneeId(),
                request.startDate(),
                request.endDate(),
                request.startTime(),
                request.endTime(),
                request.recurrenceType(),
                request.daysOfWeek(),
                request.earlyAllowanceMinutes(),
                request.lateAllowanceMinutes())
            .getId());
  }

  @Operation(
      summary = "지정 날짜 업무 수동 생성",
      description =
          "데모 또는 운영자 확인을 위해 반복 일정에 해당하는 날짜별 실제 업무를 즉시 생성합니다. 같은 날짜를 다시 호출해도 중복 생성되지 않습니다.")
  @PostMapping("/groups/{groupId}/assignments/generate")
  ApiResponse<Integer> generate(
      @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
      @PathVariable long groupId,
      @Valid @RequestBody GenerateRequest request) {
    sessions.require(token, request.managerId());
    return ApiResponse.of(
        "ASSIGNMENTS_GENERATED",
        service.generate(groupId, request.managerId(), request.targetDate()));
  }

  @Schema(description = "반복 일정 등록 요청")
  public record ScheduleRequest(
      @Schema(description = "요청 MANAGER ID", example = "1") @NotNull Long managerId,
      @Schema(description = "담당 WORKER ID. null이면 그룹 공용 업무", example = "2", nullable = true)
          Long assigneeId,
      @Schema(description = "반복 시작일", example = "2026-08-20") @NotNull LocalDate startDate,
      @Schema(description = "반복 종료일. null이면 종료일 없음", example = "2026-09-30", nullable = true)
          LocalDate endDate,
      @Schema(description = "업무 시작 시각", example = "23:00:00") @NotNull LocalTime startTime,
      @Schema(description = "업무 종료 시각", example = "06:00:00") @NotNull LocalTime endTime,
      @Schema(
              description = "반복 방식",
              example = "WEEKLY",
              allowableValues = {"ONCE", "DAILY", "WEEKLY"})
          @NotNull
          RecurrenceType recurrenceType,
      @Schema(description = "WEEKLY일 때 실행 요일", example = "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]")
          Set<DayOfWeek> daysOfWeek,
      @Schema(description = "시작 전 수행 허용 분", example = "10") @PositiveOrZero
          int earlyAllowanceMinutes,
      @Schema(description = "종료 후 수행 허용 분", example = "20") @PositiveOrZero
          int lateAllowanceMinutes) {}

  @Schema(description = "날짜별 업무 수동 생성 요청")
  public record GenerateRequest(
      @Schema(example = "1") @NotNull Long managerId,
      @Schema(description = "생성할 업무 기준일", example = "2026-08-20") @NotNull LocalDate targetDate) {}
}
