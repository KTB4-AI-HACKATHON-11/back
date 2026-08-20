package com.ktb.hackathon.team11.task;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.assignment.*;
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

@ExtendWith(MockitoExtension.class)
class TaskVerificationServiceTest {
  @Mock private TaskTemplateRepository templates;
  @Mock private TaskAssignmentRepository assignments;
  @Mock private TaskScheduleRepository schedules;
  @Mock private GroupService groups;
  @Mock private GroupMemberRepository memberships;
  @Mock private PhotoInspector photoInspector;
  @Mock private FileStorage storage;
  @Mock private TaskTemplate template;
  @Mock private WorkGroup group;
  @Mock private GroupMember managerMembership;
  @Mock private GroupMember workerMembership;
  @Mock private Member worker;
  @Mock private TaskAssignment assignment;
  @Mock private TaskItemTemplate item;
  @Mock private TaskSchedule schedule;

  private TaskVerificationService service;

  @BeforeEach
  void setUp() {
    Clock clock =
        Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    service =
        new TaskVerificationService(
            templates,
            assignments,
            schedules,
            groups,
            memberships,
            photoInspector,
            storage,
            clock);

    when(templates.findById(10L)).thenReturn(Optional.of(template));
    when(template.getGroup()).thenReturn(group);
    when(group.getId()).thenReturn(9L);
    when(groups.requireMember(9L, 1L)).thenReturn(managerMembership);
    when(managerMembership.getGroupRole()).thenReturn(MemberRole.MANAGER);
    when(workerMembership.getGroupRole()).thenReturn(MemberRole.WORKER);
    when(workerMembership.getMember()).thenReturn(worker);
    when(memberships.findByGroupIdAndMemberId(9L, 2L))
        .thenReturn(Optional.of(workerMembership));
    when(assignments.findAllByScheduleTaskTemplateId(10L)).thenReturn(List.of(assignment));
    when(assignment.getId()).thenReturn(12L);
    when(assignment.getStatus()).thenReturn(AssignmentStatus.PENDING);
    when(assignment.getTaskItemTemplate()).thenReturn(item);
    when(item.hasReferenceImage()).thenReturn(false);
    when(schedules.findFirstByTaskTemplateIdOrderByIdDesc(10L))
        .thenReturn(Optional.of(schedule));
  }

  @Test
  void savesPhotoVerificationWithoutReferencePhoto() {
    OffsetDateTime dueAt = OffsetDateTime.parse("2026-08-21T09:30:00+09:00");

    TaskVerificationService.UpdatedSettings result =
        service.update(
            10L,
            1L,
            2L,
            dueAt,
            List.of(
                new TaskVerificationService.ItemCommand(
                    12L, true, CompletionType.PHOTO, "매장 조명이 켜져 있어야 한다.", null)),
            List.of());

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().completionType()).isEqualTo(CompletionType.PHOTO);
    assertThat(result.items().getFirst().referencePhotoUrl()).isNull();
    verify(item).updateVerification(CompletionType.PHOTO, "매장 조명이 켜져 있어야 한다.");
    verifyNoInteractions(photoInspector, storage);
  }

  @Test
  void removesExistingReferencePhotoWhenItIsOmitted() {
    when(item.hasReferenceImage()).thenReturn(true, true, false);
    when(item.getReferenceImageKey()).thenReturn("groups/9/tasks/10/references/old.png");

    service.update(
        10L,
        1L,
        2L,
        OffsetDateTime.parse("2026-08-21T09:30:00+09:00"),
        List.of(
            new TaskVerificationService.ItemCommand(
                12L, true, CompletionType.PHOTO, "매장 조명이 켜져 있어야 한다.", null)),
        List.of());

    verify(item).clearReferenceImage();
  }
}
