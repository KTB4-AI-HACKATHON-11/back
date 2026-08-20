package com.ktb.hackathon.team11.agent;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.stub-enabled", havingValue = "true")
public class StubAgentAiClient implements AgentAiClient {
  @Override
  public Response respond(Request request) {
    return new Response("현재 그룹의 요청을 확인했습니다. AI 스텁 모드에서는 조회 답변만 제공합니다.", List.of());
  }
}
