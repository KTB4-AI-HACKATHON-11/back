package com.ktb.hackathon.team11.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.group.WorkGroup;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.member.MemberRole;
import com.ktb.hackathon.team11.member.MemberService;
import com.ktb.hackathon.team11.schedule.TaskSchedule;
import com.ktb.hackathon.team11.task.TaskTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {
  @Mock private TaskAssignmentRepository repository;
  @Mock private MemberService members;
  @Mock private GroupService groups;
  @Mock private TaskAssignment assignment;
  @Mock private TaskSchedule schedule;
  @Mock private TaskTemplate template;
  @Mock private WorkGroup group;
  @Mock private Member worker;
  @InjectMocks private AssignmentService service;

  @Test
  void updatesCheckChecklistToPerformed() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);
    service = new AssignmentService(repository, members, groups, clock);
    when(repository.findByIdAndScheduleTaskTemplateId(940001L, 930001L))
        .thenReturn(Optional.of(assignment));
    when(assignment.getSchedule()).thenReturn(schedule);
    when(schedule.getTaskTemplate()).thenReturn(template);
    when(template.getGroup()).thenReturn(group);
    when(group.getId()).thenReturn(9L);
    when(assignment.getAssignee()).thenReturn(worker);
    when(worker.getId()).thenReturn(2L);

    TaskAssignment result = service.updatePerformed(930001L, 940001L, 2L, true);

    assertThat(result).isSameAs(assignment);
    verify(members).requireRole(2L, MemberRole.WORKER);
    verify(groups).requireMember(9L, 2L);
    verify(assignment).completeCheck(any());
  }
}
