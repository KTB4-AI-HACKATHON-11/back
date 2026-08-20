package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.assignment.TaskAssignment;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record TaskRunId(long scheduleId, LocalDate scheduledDate) {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  public static TaskRunId from(TaskAssignment assignment) {
    return new TaskRunId(assignment.getSchedule().getId(), assignment.getScheduledDate());
  }

  public static TaskRunId parse(String value) {
    if (value == null || !value.matches("r[1-9][0-9]*-[0-9]{8}"))
      throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    int separator = value.indexOf('-');
    try {
      long scheduleId = Long.parseLong(value.substring(1, separator));
      LocalDate date = LocalDate.parse(value.substring(separator + 1), DATE_FORMAT);
      return new TaskRunId(scheduleId, date);
    } catch (NumberFormatException | DateTimeParseException exception) {
      throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    }
  }

  public String value() {
    return "r" + scheduleId + "-" + scheduledDate.format(DATE_FORMAT);
  }
}
