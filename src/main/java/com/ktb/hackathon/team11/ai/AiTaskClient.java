package com.ktb.hackathon.team11.ai;

import java.util.List;

public interface AiTaskClient {
  List<GeneratedTask> generateTasks(String message);

  PhotoCheckResult checkPhoto(PhotoCheckCommand command);

  String answerKnowledge(String information, String question);
}
