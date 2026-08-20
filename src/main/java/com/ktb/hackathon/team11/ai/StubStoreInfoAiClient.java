package com.ktb.hackathon.team11.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.stub-enabled", havingValue = "true")
public class StubStoreInfoAiClient implements StoreInfoAnswerClient {
  @Override
  public String answer(String question, String information) {
    return "AI 스텁 응답입니다.";
  }
}
