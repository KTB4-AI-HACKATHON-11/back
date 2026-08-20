package com.ktb.hackathon.team11.knowledge;

import com.ktb.hackathon.team11.ai.AiTaskClient;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.store.StoreInfo;
import com.ktb.hackathon.team11.store.StoreInfoCategory;
import com.ktb.hackathon.team11.store.StoreInfoRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

  private final AiTaskClient ai;
  private final StoreInfoRepository infos;
  private final GroupService groups;
  private final ConcurrentMap<String, Conversation> conversations = new ConcurrentHashMap<>();

  private static final int MAX_QUESTION_LENGTH = 60_000;

  public KnowledgeService(AiTaskClient ai, StoreInfoRepository infos, GroupService groups) {
    this.ai = ai;
    this.infos = infos;
    this.groups = groups;
  }

  public AnswerResponse answer(
      long groupId, long requesterId, String conversationId, String question) {
    groups.requireGroup(groupId);
    groups.requireMember(groupId, requesterId);
    List<StoreInfo> items = infos.findAllByGroupIdOrderByCategoryAscIdAsc(groupId);
    if (items.isEmpty()) {
      return new AnswerResponse(
          "아직 등록된 매장 정보가 없어요. 매니저에게 문의해 주세요.", conversationId);
    }
    String information = format(items);
    String id = conversationId == null || conversationId.isBlank()
        ? UUID.randomUUID().toString()
        : groupId + ":" + conversationId;
    Conversation conversation = conversations.computeIfAbsent(id, ignored -> new Conversation());
    synchronized (conversation) {
      String prompt = questionWithHistory(conversation, question);
      String answer = ai.answerKnowledge(information, prompt);
      conversation.add(question, answer);
      return new AnswerResponse(answer, conversationId == null ? id : conversationId);
    }
  }

  private String questionWithHistory(Conversation conversation, String question) {
    if (conversation.turns.isEmpty()) return question;
    StringBuilder history = new StringBuilder("[이전 대화]\n");
    for (Turn turn : conversation.turns) {
      history.append("질문: ").append(turn.question()).append('\n');
      history.append("AI 답변: ").append(turn.answer()).append('\n');
    }
    int available = MAX_QUESTION_LENGTH - question.length() - "\n[현재 질문]\n".length();
    if (available > 0 && history.length() > available) {
      history = new StringBuilder(history.substring(history.length() - available));
    }
    if (available <= 0) history.setLength(0);
    history.append("\n[현재 질문]\n").append(question);
    return history.toString();
  }

  private String format(List<StoreInfo> items) {
    StringBuilder result = new StringBuilder();
    StoreInfoCategory previous = null;
    for (StoreInfo info : items) {
      if (info.getCategory() != previous) {
        if (!result.isEmpty()) result.append('\n');
        result.append('[').append(categoryLabel(info.getCategory())).append("]\n");
        previous = info.getCategory();
      }
      result.append(info.getTitle()).append(": ").append(info.getContent()).append('\n');
    }
    return result.toString();
  }

  private String categoryLabel(StoreInfoCategory category) {
    return switch (category) {
      case LOCATION -> "매장 위치";
      case PROMOTION -> "프로모션";
      case DELIVERY -> "택배·입고";
      case EQUIPMENT -> "장비";
      case RULE -> "운영 규칙";
      case ETC -> "기타";
    };
  }

  public record AnswerResponse(String answer, String conversationId) {}

  private static final class Conversation {
    private final Deque<Turn> turns = new ArrayDeque<>();

    private void add(String question, String answer) {
      turns.addLast(new Turn(question, answer));
    }
  }

  private record Turn(String question, String answer) {}
}
