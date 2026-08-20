package com.ktb.hackathon.team11.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskQueryControllerTest {
  @Mock private TaskQueryService service;
  @InjectMocks private TaskQueryController controller;

  @Test
  void listsTasksWithPaginationAndStatus() {
    TaskQueryService.TaskSummary summary =
        new TaskQueryService.TaskSummary(
            930001L,
            "오픈 전 매장 점검",
            920002L,
            "서연",
            OffsetDateTime.parse("2026-08-21T09:30:00+09:00"),
            TaskStatus.IN_PROGRESS,
            6,
            4,
            67,
            true);
    TaskQueryService.TaskListResponse data =
        new TaskQueryService.TaskListResponse(1, List.of(summary));
    when(service.list(9L, 920001L, 0, 20, TaskStatus.IN_PROGRESS)).thenReturn(data);

    ApiResponse<TaskQueryService.TaskListResponse> response =
        controller.list(9L, 920001L, 0, 20, TaskStatus.IN_PROGRESS);

    assertThat(response.getCode()).isEqualTo("TASK_LIST_FOUND");
    assertThat(response.getData().totalCount()).isEqualTo(1);
    assertThat(response.getData().items().get(0).progress()).isEqualTo(67);
    verify(service).list(9L, 920001L, 0, 20, TaskStatus.IN_PROGRESS);
  }

  @Test
  void returnsTaskDetail() {
    TaskQueryService.TaskDetail detail =
        new TaskQueryService.TaskDetail(
            930001L,
            9L,
            "오픈 전 매장 점검",
            "POS와 매장 상태를 확인해줘",
            920001L,
            "민준",
            920002L,
            "서연",
            OffsetDateTime.parse("2026-08-21T09:30:00+09:00"),
            TaskStatus.IN_PROGRESS,
            67,
            false,
            OffsetDateTime.parse("2026-08-20T10:00:00+09:00"),
            List.of(
                new TaskQueryService.Checklist(
                    940001L,
                    1,
                    "POS 전원 확인",
                    "POS 화면을 확인해 주세요.",
                    CompletionType.PHOTO,
                    "POS가 켜져 있어야 합니다.",
                    true,
                    "https://example.com/reference.png",
                    false,
                    null,
                    null)));
    when(service.detail(930001L, 920002L)).thenReturn(detail);

    ApiResponse<TaskQueryService.TaskDetail> response = controller.detail(930001L, 920002L);

    assertThat(response.getCode()).isEqualTo("TASK_FOUND");
    assertThat(response.getData().checklists()).hasSize(1);
    assertThat(response.getData().checklists().get(0).completionType()).isEqualTo(CompletionType.PHOTO);
    verify(service).detail(930001L, 920002L);
  }
}
