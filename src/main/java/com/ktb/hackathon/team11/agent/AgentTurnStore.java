package com.ktb.hackathon.team11.agent;

import com.ktb.hackathon.team11.group.GroupMember;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AgentTurnStore {
  private static final int RETAINED_TURNS = 100;
  private final AgentTurnRepository turns;
  private final PlatformTransactionManager transactionManager;

  public CreatedTurn create(GroupMember manager, String requestId, String message) {
    var existing = turns.findByGroupIdAndRequestId(manager.getGroup().getId(), requestId);
    if (existing.isPresent()) return new CreatedTurn(existing.get(), false);
    try {
      AgentTurn created =
          new TransactionTemplate(transactionManager)
              .execute(
                  ignored ->
                      turns.saveAndFlush(
                          new AgentTurn(
                              manager.getGroup(), manager.getMember(), requestId, message)));
      return new CreatedTurn(
          java.util.Objects.requireNonNull(created, "에이전트 대화를 저장하지 못했습니다."), true);
    } catch (DataIntegrityViolationException exception) {
      return new CreatedTurn(
          turns
              .findByGroupIdAndRequestId(manager.getGroup().getId(), requestId)
              .orElseThrow(() -> exception),
          false);
    }
  }

  @Transactional(readOnly = true)
  public AgentTurn find(long groupId, String requestId) {
    return turns
        .findByGroupIdAndRequestId(groupId, requestId)
        .orElseThrow(
            () ->
                new com.ktb.hackathon.team11.global.exception.BusinessException(
                    com.ktb.hackathon.team11.global.exception.ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public List<AgentTurn> history(long groupId, int limit) {
    List<AgentTurn> result =
        turns.findAllByGroupIdOrderByIdDesc(
            groupId, PageRequest.of(0, Math.min(limit, RETAINED_TURNS)));
    Collections.reverse(result);
    return result;
  }

  @Transactional
  public AgentTurn complete(
      long turnId, String message, String cardsJson, String toolResultsJson) {
    AgentTurn turn = turns.findWithManagerById(turnId).orElseThrow();
    turn.complete(message, cardsJson, toolResultsJson);
    prune(turn.getGroup().getId());
    return turn;
  }

  @Transactional
  public void progress(
      long turnId, String cardsJson, String toolResultsJson, String toolActivitiesJson) {
    AgentTurn turn = turns.findById(turnId).orElseThrow();
    turn.recordProgress(cardsJson, toolResultsJson, toolActivitiesJson);
  }

  @Transactional
  public AgentTurn fail(long turnId, String message) {
    AgentTurn turn = turns.findById(turnId).orElseThrow();
    turn.fail(message);
    prune(turn.getGroup().getId());
    return turn;
  }

  @Transactional
  public AgentTurn fail(long turnId, String message, String toolActivitiesJson) {
    AgentTurn turn = turns.findById(turnId).orElseThrow();
    turn.fail(message, toolActivitiesJson);
    prune(turn.getGroup().getId());
    return turn;
  }

  private void prune(long groupId) {
    List<AgentTurn> overflow =
        turns.findAllByGroupIdOrderByIdDesc(groupId, PageRequest.of(0, RETAINED_TURNS + 20));
    if (overflow.size() > RETAINED_TURNS) {
      turns.deleteAllInBatch(overflow.subList(RETAINED_TURNS, overflow.size()));
    }
  }

  public record CreatedTurn(AgentTurn turn, boolean created) {}
}
