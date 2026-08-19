package com.ktb.hackathon.team11.ai;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.stub-enabled", havingValue = "true", matchIfMissing = true)
public class StubAiTaskClient implements AiTaskClient {
  public List<GeneratedTask> generateTasks(String message) {
    return List.of(
        new GeneratedTask(
            "POS 전원 확인",
            "POS 화면이 보이도록 촬영해 주세요.",
            CompletionType.PHOTO,
            "POS 화면이 켜져 있고 정상 화면이 표시되어야 한다."),
        new GeneratedTask("매장 바닥 청소", "바닥 청소를 마친 뒤 완료를 체크해 주세요.", CompletionType.CHECK, null));
  }

  public PhotoCheckResult checkPhoto(PhotoCheckCommand command) {
    if (command.url().toLowerCase().contains("retake"))
      return new PhotoCheckResult(
          PhotoCheckStatus.RETAKE, "사진에서 기준을 확인하기 어렵습니다.", "기준 대상이 선명하게 보이도록 다시 촬영해 주세요.");
    return new PhotoCheckResult(PhotoCheckStatus.PASS, "사진에서 업무 기준을 충족한 것을 확인했습니다.", null);
  }
}
