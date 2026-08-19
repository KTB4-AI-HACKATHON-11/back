package com.ktb.hackathon.team11.schedule;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.task.TaskTemplate;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "task_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskSchedule extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private TaskTemplate taskTemplate;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member assignee;

  @Column(nullable = false)
  private LocalDate startDate;

  private LocalDate endDate;

  @Column(nullable = false)
  private LocalTime startTime;

  @Column(nullable = false)
  private LocalTime endTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RecurrenceType recurrenceType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "task_schedule_days", joinColumns = @JoinColumn(name = "schedule_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week")
  private Set<DayOfWeek> daysOfWeek = new HashSet<>();

  private int earlyAllowanceMinutes;
  private int lateAllowanceMinutes;
  private boolean active = true;

  public TaskSchedule(
      TaskTemplate t,
      Member a,
      LocalDate sd,
      LocalDate ed,
      LocalTime st,
      LocalTime et,
      RecurrenceType rt,
      Set<DayOfWeek> days,
      int early,
      int late) {
    if (ed != null && ed.isBefore(sd)
        || early < 0
        || late < 0
        || rt == RecurrenceType.WEEKLY && (days == null || days.isEmpty()))
      throw new BusinessException(ErrorCode.INVALID_SCHEDULE);
    taskTemplate = t;
    assignee = a;
    startDate = sd;
    endDate = ed;
    startTime = st;
    endTime = et;
    recurrenceType = rt;
    if (days != null) daysOfWeek.addAll(days);
    earlyAllowanceMinutes = early;
    lateAllowanceMinutes = late;
  }

  public boolean occursOn(LocalDate date) {
    if (!active || date.isBefore(startDate) || endDate != null && date.isAfter(endDate))
      return false;
    return switch (recurrenceType) {
      case ONCE -> date.equals(startDate);
      case DAILY -> true;
      case WEEKLY -> daysOfWeek.contains(date.getDayOfWeek());
    };
  }

  public Window windowFor(LocalDate date) {
    LocalDateTime start = date.atTime(startTime);
    LocalDateTime end = date.atTime(endTime);
    if (!endTime.isAfter(startTime)) end = end.plusDays(1);
    return new Window(
        start.minusMinutes(earlyAllowanceMinutes), end.plusMinutes(lateAllowanceMinutes));
  }

  public record Window(LocalDateTime availableFrom, LocalDateTime dueAt) {}
}
