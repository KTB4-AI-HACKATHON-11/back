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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

  private final AiTaskClient ai;
  private final StoreInfoRepository infos;
  private final GroupService groups;
  private final ConcurrentMap<String, Conversation> conversations = new ConcurrentHashMap<>();

  private static final int MAX_QUESTION_LENGTH = 60_000;
  private static final int MAX_TURNS_PER_CONVERSATION = 30;
  private static final int MAX_CONVERSATIONS = 1_000;
  private static final long CONVERSATION_TTL_MILLIS = 6 * 60 * 60 * 1_000L;

  public KnowledgeService(AiTaskClient ai, StoreInfoRepository infos, GroupService groups) {
    this.ai = ai;
    this.infos = infos;
    this.groups = groups;
  }

  public AnswerResponse answer(
      long groupId, long requesterId, String conversationId, String question) {
    groups.requireGroup(groupId);
    groups.requireMember(groupId, requesterId);
    String publicId =
        conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId;
    String key = groupId + ":" + requesterId + ":" + publicId;
    List<StoreInfo> items = infos.findAllByGroupIdOrderByCategoryAscIdAsc(groupId);
    if (items.isEmpty()) {
      return new AnswerResponse(
          "아직 등록된 매장 정보가 없어요. 매니저에게 문의해 주세요.", publicId);
    }
    String information = format(items);
    if (!conversations.containsKey(key)) evictIfFull();
    Conversation conversation = conversations.computeIfAbsent(key, ignored -> new Conversation());
    synchronized (conversation) {
      conversation.touch();
      String prompt = questionWithHistory(conversation, question);
      String answer = ai.answerKnowledge(information, prompt);
      conversation.add(question, answer);
      return new AnswerResponse(answer, publicId);
    }
  }

  @Scheduled(fixedDelayString = "${knowledge.conversation-cleanup-ms:600000}")
  void removeExpiredConversations() {
    long cutoff = System.currentTimeMillis() - CONVERSATION_TTL_MILLIS;
    conversations.entrySet().removeIf(entry -> entry.getValue().lastAccessMillis < cutoff);
  }

  private void evictIfFull() {
    if (conversations.size() < MAX_CONVERSATIONS) return;
    removeExpiredConversations();
    if (conversations.size() < MAX_CONVERSATIONS) return;
    conversations.entrySet().stream()
        .min(java.util.Comparator.comparingLong(entry -> entry.getValue().lastAccessMillis))
        .ifPresent(entry -> conversations.remove(entry.getKey(), entry.getValue()));
  }

  private String questionWithHistory(Conversation conversation, String question) {
    if (conversation.turns.isEmpty()) return question;

    String currentQuestion = "\n[현재 질문]\n" + question;
    int available =
        MAX_QUESTION_LENGTH - currentQuestion.length() - "[이전 대화]\n".length();
    if (available <= 0) return question;

    Deque<String> selectedTurns = new ArrayDeque<>();
    int selectedLength = 0;
    var iterator = conversation.turns.descendingIterator();
    while (iterator.hasNext()) {
      Turn turn = iterator.next();
      String block = "질문: " + turn.question() + '\n' + "AI 답변: " + turn.answer() + '\n';
      if (selectedLength + block.length() > available) break;
      selectedTurns.addFirst(block);
      selectedLength += block.length();
    }
    if (selectedTurns.isEmpty()) return question;

    StringBuilder prompt = new StringBuilder("[이전 대화]\n");
    selectedTurns.forEach(prompt::append);
    return prompt.append(currentQuestion).toString();
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
    private volatile long lastAccessMillis = System.currentTimeMillis();

    private void touch() {
      lastAccessMillis = System.currentTimeMillis();
    }

    private void add(String question, String answer) {
      turns.addLast(new Turn(question, answer));
      while (turns.size() > MAX_TURNS_PER_CONVERSATION) turns.removeFirst();
      touch();
    }
  }

  private record Turn(String question, String answer) {}
}
