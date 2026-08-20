package com.ktb.hackathon.team11.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb.hackathon.team11.ai.AiTaskClient;
import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.ai.GeneratedTask;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.storage.FileStorage;
import com.ktb.hackathon.team11.storage.PhotoInspector;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskChecklistGenerationServiceTest {

  @Mock private TaskTemplateRepository templates;
  @Mock private TaskItemTemplateRepository items;
  @Mock private GroupService groups;
  @Mock private AiTaskClient ai;
  @Mock private PhotoInspector photoInspector;
  @Mock private FileStorage storage;
  @InjectMocks private TaskTemplateService service;

  @Test
  void generatesStatelessChecklistWithSequence() {
    String message = "조명을 켜고 카운터를 정리해줘";
    OffsetDateTime dueAt = OffsetDateTime.parse("2026-08-21T09:30:00+09:00");
    when(ai.generateTasks(message))
        .thenReturn(
            List.of(
                new GeneratedTask(
                    "조명 점등 확인",
                    "매장 전체 조명이 보이도록 촬영해 주세요.",
                    CompletionType.PHOTO,
                    "사진에서 매장 조명이 켜져 있어야 한다."),
                new GeneratedTask(
                    "카운터 정리", "카운터 정리를 마친 뒤 완료를 체크해 주세요.", CompletionType.CHECK, null)));

    TaskTemplateService.GeneratedTasksResponse response =
        service.generateTasks(1L, 1L, "  오픈 전 매장 점검  ", message, "  서연  ", dueAt);

    verify(groups).requireManager(1L, 1L);
    verify(ai).generateTasks(message);
    verifyNoInteractions(templates, items, photoInspector, storage);
    assertThat(response.title()).isEqualTo("오픈 전 매장 점검");
    assertThat(response.assigneeName()).isEqualTo("서연");
    assertThat(response.dueAt()).isEqualTo(dueAt);
    assertThat(response.checklists()).hasSize(2);
    assertThat(response.checklists().get(0).sequence()).isEqualTo(1);
    assertThat(response.checklists().get(0).completionType()).isEqualTo(CompletionType.PHOTO);
    assertThat(response.checklists().get(1).sequence()).isEqualTo(2);
    assertThat(response.checklists().get(1).completionType()).isEqualTo(CompletionType.CHECK);
  }

  @Test
  void rejectsEmptyAiTaskList() {
    when(ai.generateTasks("업무를 생성해줘")).thenReturn(List.of());

    assertAiUnavailable(
        () ->
            service.generateTasks(
                1L,
                1L,
                "오픈 점검",
                "업무를 생성해줘",
                "서연",
                OffsetDateTime.parse("2026-08-21T09:30:00+09:00")));
  }

  @Test
  void rejectsPhotoTaskWithoutRule() {
    when(ai.generateTasks("업무를 생성해줘"))
        .thenReturn(
            List.of(
                new GeneratedTask(
                    "조명 확인", "조명을 촬영해 주세요.", CompletionType.PHOTO, null)));

    assertAiUnavailable(
        () ->
            service.generateTasks(
                1L,
                1L,
                "오픈 점검",
                "업무를 생성해줘",
                "서연",
                OffsetDateTime.parse("2026-08-21T09:30:00+09:00")));
  }

  @Test
  void rejectsCheckTaskWithRule() {
    when(ai.generateTasks("업무를 생성해줘"))
        .thenReturn(
            List.of(
                new GeneratedTask(
                    "카운터 정리",
                    "정리 후 체크해 주세요.",
                    CompletionType.CHECK,
                    "카운터가 정리되어야 한다.")));

    assertAiUnavailable(
        () ->
            service.generateTasks(
                1L,
                1L,
                "오픈 점검",
                "업무를 생성해줘",
                "서연",
                OffsetDateTime.parse("2026-08-21T09:30:00+09:00")));
  }

  private void assertAiUnavailable(Runnable invocation) {
    assertThatThrownBy(invocation::run)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.AI_UNAVAILABLE);
  }
}
