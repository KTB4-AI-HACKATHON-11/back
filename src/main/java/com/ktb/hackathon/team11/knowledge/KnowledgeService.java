package com.ktb.hackathon.team11.knowledge;

import com.ktb.hackathon.team11.ai.AiTaskClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

  private final AiTaskClient ai;
  private final String information;

  public KnowledgeService(
      AiTaskClient ai, @Value("classpath:store-knowledge.txt") Resource informationResource) {
    this.ai = ai;
    this.information = readInformation(informationResource);
  }

  public AnswerResponse answer(String question) {
    return new AnswerResponse(ai.answerKnowledge(information, question));
  }

  private String readInformation(Resource resource) {
    try {
      String value = resource.getContentAsString(StandardCharsets.UTF_8).strip();
      if (value.isBlank() || value.length() > 60_000) {
        throw new IllegalStateException("Store knowledge must contain 1 to 60000 characters");
      }
      return value;
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to load store knowledge", exception);
    }
  }

  public record AnswerResponse(String answer) {}
}
