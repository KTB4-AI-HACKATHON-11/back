package com.ktb.hackathon.team11.knowledge;

import com.ktb.hackathon.team11.ai.AiTaskClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

  private final AiTaskClient ai;
  private final String information;
  private final ConcurrentMap<String, Conversation> conversations = new ConcurrentHashMap<>();

  private static final int MAX_TURNS = 10;
  private static final int MAX_INFORMATION_LENGTH = 60_000;

  public KnowledgeService(
      AiTaskClient ai, @Value("classpath:store-knowledge.txt") Resource informationResource) {
    this.ai = ai;
    this.information = readInformation(informationResource);
  }

  public AnswerResponse answer(String conversationId, String question) {
    String id = conversationId == null || conversationId.isBlank()
        ? UUID.randomUUID().toString()
        : conversationId;
    Conversation conversation = conversations.computeIfAbsent(id, ignored -> new Conversation());
    synchronized (conversation) {
      String context = informationWithHistory(conversation);
      String answer = ai.answerKnowledge(context, question);
      conversation.add(question, answer);
      return new AnswerResponse(answer, id);
    }
  }

  private String informationWithHistory(Conversation conversation) {
    if (conversation.turns.isEmpty()) return information;
    StringBuilder history = new StringBuilder("\n\n[이전 대화]\n");
    for (Turn turn : conversation.turns) {
      history.append("질문: ").append(turn.question()).append('\n');
      history.append("AI 답변: ").append(turn.answer()).append('\n');
    }
    int available = MAX_INFORMATION_LENGTH - information.length();
    if (available <= 0) return information;
    if (history.length() > available) history.setLength(available);
    return information + history;
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

  public record AnswerResponse(String answer, String conversationId) {}

  private static final class Conversation {
    private final Deque<Turn> turns = new ArrayDeque<>();

    private void add(String question, String answer) {
      turns.addLast(new Turn(question, answer));
      while (turns.size() > MAX_TURNS) turns.removeFirst();
    }
  }

  private record Turn(String question, String answer) {}
}
