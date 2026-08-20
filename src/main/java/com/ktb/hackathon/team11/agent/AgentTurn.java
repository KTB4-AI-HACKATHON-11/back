package com.ktb.hackathon.team11.agent;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.group.WorkGroup;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "group_agent_turns",
    indexes = @Index(name = "idx_group_agent_turn_history", columnList = "group_id,id"),
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_group_agent_turn_request",
            columnNames = {"group_id", "request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentTurn extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private WorkGroup group;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "manager_id", nullable = false)
  private Member manager;

  @Column(name = "request_id", nullable = false, length = 36)
  private String requestId;

  @Lob
  @Column(nullable = false, columnDefinition = "TEXT")
  private String userMessage;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String assistantMessage;

  @Lob
  @Column(nullable = false, columnDefinition = "TEXT")
  private String notificationCardsJson = "[]";

  @Lob
  @Column(nullable = false, columnDefinition = "TEXT")
  private String toolResultsJson = "[]";

  @Lob
  @Column(nullable = false, columnDefinition = "TEXT")
  private String toolActivitiesJson = "[]";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AgentTurnStatus status = AgentTurnStatus.PROCESSING;

  public AgentTurn(WorkGroup group, Member manager, String requestId, String userMessage) {
    this.group = group;
    this.manager = manager;
    this.requestId = requestId;
    this.userMessage = userMessage;
  }

  public void complete(String message, String cardsJson, String resultsJson) {
    assistantMessage = message;
    notificationCardsJson = cardsJson;
    toolResultsJson = resultsJson;
    status = AgentTurnStatus.COMPLETED;
  }

  public void recordProgress(String cardsJson, String resultsJson, String activitiesJson) {
    notificationCardsJson = cardsJson;
    toolResultsJson = resultsJson;
    toolActivitiesJson = activitiesJson;
  }

  public void fail(String message) {
    assistantMessage = message;
    status = AgentTurnStatus.FAILED;
  }

  public void fail(String message, String activitiesJson) {
    toolActivitiesJson = activitiesJson;
    fail(message);
  }
}
