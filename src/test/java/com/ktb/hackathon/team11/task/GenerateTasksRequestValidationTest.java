package com.ktb.hackathon.team11.task;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GenerateTasksRequestValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void acceptsValidRequest() {
    TaskTemplateController.GenerateTasksRequest request =
        new TaskTemplateController.GenerateTasksRequest(
            1L,
            "오픈 전 매장 점검",
            "조명을 켜고 카운터를 정리해줘",
            "서연",
            OffsetDateTime.now().plusDays(1));

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsBlankFieldsAndPastDueDate() {
    TaskTemplateController.GenerateTasksRequest request =
        new TaskTemplateController.GenerateTasksRequest(
            1L, " ", " ", " ", OffsetDateTime.now().minusMinutes(1));

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("title", "message", "assigneeName", "dueAt");
  }
}
