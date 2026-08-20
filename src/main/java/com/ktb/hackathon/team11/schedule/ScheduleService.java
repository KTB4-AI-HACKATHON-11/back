package com.ktb.hackathon.team11.schedule;

import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.task.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleService {
  private final TaskScheduleRepository schedules;
  private final TaskAssignmentRepository assignments;
  private final TaskTemplateService templates;
  private final GroupService groups;
  private final TaskItemTemplateRepository items;
  private final Clock clock;

  @Transactional
  public TaskSchedule create(
      long templateId,
      long managerId,
      Long assigneeId,
      LocalDate sd,
      LocalDate ed,
      LocalTime st,
      LocalTime et,
      RecurrenceType rt,
      Set<DayOfWeek> days,
      int early,
      int late) {
    TaskTemplate t = templates.require(templateId);
    groups.requireManager(t.getGroup().getId(), managerId);
    Member a = null;
    if (assigneeId != null) {
      a = groups.requireWorker(t.getGroup().getId(), assigneeId).getMember();
    }
    return schedules.save(new TaskSchedule(t, a, sd, ed, st, et, rt, days, early, late));
  }

  @Transactional
  public int generate(long groupId, long managerId, LocalDate date) {
    groups.requireManager(groupId, managerId);
    return generateGroup(groupId, date);
  }

  private int generateGroup(long groupId, LocalDate date) {
    List<TaskSchedule> groupSchedules =
        new ArrayList<>(
            schedules
                .findAllByTaskTemplateGroupIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                    groupId, date, date));
    groupSchedules.addAll(
        schedules
            .findAllByTaskTemplateGroupIdAndActiveTrueAndStartDateLessThanEqualAndEndDateIsNull(
                groupId, date));
    return generateSchedules(groupSchedules, date);
  }

  private int generateSchedules(List<TaskSchedule> candidateSchedules, LocalDate date) {
    int count = 0;
    for (TaskSchedule s : candidateSchedules)
      if (s.occursOn(date)) {
        TaskSchedule.Window w = s.windowFor(date);
        for (TaskItemTemplate i :
            items.findAllByTaskTemplateIdOrderBySequence(s.getTaskTemplate().getId()))
          if (!assignments.existsByScheduleIdAndTaskItemTemplateIdAndScheduledDate(
              s.getId(), i.getId(), date)) {
            assignments.save(new TaskAssignment(s, i, date, w.availableFrom(), w.dueAt()));
            count++;
          }
      }
    return count;
  }

  @Scheduled(cron = "${task.assignment-generation-cron:0 0 0 * * *}", zone = "Asia/Seoul")
  @Transactional
  public void generateTomorrow() {
    LocalDate d = LocalDate.now(clock).plusDays(1);
    List<TaskSchedule> activeSchedules =
        new ArrayList<>(
            schedules.findAllByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(d, d));
    activeSchedules.addAll(
        schedules.findAllByActiveTrueAndStartDateLessThanEqualAndEndDateIsNull(d));
    generateSchedules(activeSchedules, d);
  }
}
