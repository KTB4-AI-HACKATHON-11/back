package com.ktb.hackathon.team11.task;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.*;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.schedule.*;
import com.ktb.hackathon.team11.storage.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskRegistrationServiceTest {
  @Mock private TaskTemplateRepository templates;
  @Mock private TaskItemTemplateRepository items;
  @Mock private TaskScheduleRepository schedules;
  @Mock private TaskAssignmentRepository assignments;
  @Mock private GroupService groups;
  @Mock private GroupMemberRepository memberships;
  @Mock private MemberService members;
  @Mock private PhotoInspector photoInspector;
  @Mock private FileStorage storage;
  @Mock private GroupMember managerMembership;
  @Mock private WorkGroup group;
  @Mock private Member manager;
  @Mock private Member worker;

  private TaskRegistrationService service;
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  @BeforeEach
  void setUp() {
    service =
        new TaskRegistrationService(
            templates,
            items,
            schedules,
            assignments,
            groups,
            memberships,
            members,
            photoInspector,
            storage,
            clock);
    when(groups.requireManager(1L, 1L)).thenReturn(managerMembership);
    when(managerMembership.getGroup()).thenReturn(group);
    when(managerMembership.getMember()).thenReturn(manager);
    when(members.requireMember(2L)).thenReturn(worker);
    when(worker.getRole()).thenReturn(MemberRole.WORKER);
    when(worker.getId()).thenReturn(2L);
    when(worker.getNickname()).thenReturn("서연");
    when(memberships.findByGroupIdAndMemberId(1L, 2L))
        .thenReturn(Optional.of(mock(GroupMember.class)));
    when(templates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(schedules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void savesPhotoToStorageAndCreatesCheckAndPhotoAssignments() {
    MockMultipartFile reference =
        new MockMultipartFile("referencePhotos", "reference.jpg", "image/jpeg", new byte[] {1});
    PhotoInspector.InspectedPhoto inspected =
        new PhotoInspector.InspectedPhoto(
            new byte[] {1}, "image/jpeg", "jpg", 1, "a".repeat(64));
    when(photoInspector.inspect(reference)).thenReturn(inspected);

    TaskRegistrationService.TaskCreatedResponse response =
        service.create(
            1L,
            1L,
            "오픈 점검",
            "조명과 카운터를 확인해줘",
            2L,
            OffsetDateTime.parse("2026-08-21T09:30:00+09:00"),
            List.of(
                new TaskRegistrationService.ChecklistCommand(
                    1, "조명 확인", "조명을 촬영해 주세요.", CompletionType.PHOTO, "조명이 켜져 있어야 한다.", 0),
                new TaskRegistrationService.ChecklistCommand(
                    2, "카운터 정리", "정리 후 체크해 주세요.", CompletionType.CHECK, null, null)),
            List.of(reference));

    verify(storage).store(contains("/references/"), eq(inspected.bytes()), eq("image/jpeg"));
    verify(assignments, times(2)).save(any(TaskAssignment.class));
    ArgumentCaptor<TaskSchedule> scheduleCaptor = ArgumentCaptor.forClass(TaskSchedule.class);
    verify(schedules).save(scheduleCaptor.capture());
    TaskSchedule.Window window =
        scheduleCaptor.getValue().windowFor(LocalDate.of(2026, 8, 20));
    assertThat(window.availableFrom()).isEqualTo(LocalDateTime.of(2026, 8, 20, 9, 0));
    assertThat(window.dueAt()).isEqualTo(LocalDateTime.of(2026, 8, 21, 9, 30));
    assertThat(response.worker().workerId()).isEqualTo(2L);
    assertThat(response.checklists()).hasSize(2);
    assertThat(response.checklists().get(0).referencePhotoAttached()).isTrue();
    assertThat(response.checklists().get(1).referencePhotoAttached()).isFalse();
  }

  @Test
  void rejectsPhotoChecklistWithoutReferencePhoto() {
    assertError(
        ErrorCode.REFERENCE_PHOTO_REQUIRED,
        () ->
            service.create(
                1L,
                1L,
                "오픈 점검",
                "조명을 확인해줘",
                2L,
                OffsetDateTime.parse("2026-08-21T09:30:00+09:00"),
                List.of(
                    new TaskRegistrationService.ChecklistCommand(
                        1, "조명 확인", "촬영해 주세요.", CompletionType.PHOTO, "켜져 있어야 한다.", null)),
                List.of()));
    verifyNoInteractions(storage, templates, items, schedules, assignments);
  }

  @Test
  void rejectsWorkerOutsideGroup() {
    when(memberships.findByGroupIdAndMemberId(1L, 2L)).thenReturn(Optional.empty());

    assertError(
        ErrorCode.WORKER_NOT_IN_GROUP,
        () ->
            service.create(
                1L,
                1L,
                "오픈 점검",
                "카운터를 정리해줘",
                2L,
                OffsetDateTime.parse("2026-08-21T09:30:00+09:00"),
                List.of(
                    new TaskRegistrationService.ChecklistCommand(
                        1, "카운터 정리", "체크해 주세요.", CompletionType.CHECK, null, null)),
                List.of()));
  }

  @Test
  void rejectsPhotoIndexUsedTwice() {
    MockMultipartFile reference =
        new MockMultipartFile("referencePhotos", "reference.jpg", "image/jpeg", new byte[] {1});
    List<TaskRegistrationService.ChecklistCommand> commands =
        List.of(
            new TaskRegistrationService.ChecklistCommand(
                1, "조명 확인", "촬영해 주세요.", CompletionType.PHOTO, "켜져 있어야 한다.", 0),
            new TaskRegistrationService.ChecklistCommand(
                2, "POS 확인", "촬영해 주세요.", CompletionType.PHOTO, "켜져 있어야 한다.", 0));

    assertError(
        ErrorCode.INVALID_REFERENCE_PHOTO_INDEX,
        () ->
            service.create(
                1L,
                1L,
                "오픈 점검",
                "확인해줘",
                2L,
                OffsetDateTime.parse("2026-08-21T09:30:00+09:00"),
                commands,
                List.of(reference)));
  }

  private void assertError(ErrorCode code, Runnable invocation) {
    assertThatThrownBy(invocation::run)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(code);
  }
}
