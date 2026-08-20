package com.ktb.hackathon.team11.agent;

import com.ktb.hackathon.team11.agent.AgentAiClient.ConversationMessage;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.group.GroupMember;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.notification.AgentNotificationService;
import com.ktb.hackathon.team11.store.StoreInfoService;
import com.ktb.hackathon.team11.task.AgentTaskMutationService;
import com.ktb.hackathon.team11.task.TaskRegistrationService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class GroupAgentService {
  private static final Logger log = LoggerFactory.getLogger(GroupAgentService.class);
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
  private static final int MODEL_HISTORY_TURNS = 30;
  private static final Duration PROCESSING_STALE_AFTER = Duration.ofMinutes(2);
  private static final String INTERRUPTED_MESSAGE =
      "처리가 중단되었습니다. 일부 변경이 반영됐을 수 있으니 현재 상태를 확인한 뒤 다시 요청해 주세요.";

  private final AgentAiClient ai;
  private final AgentTurnStore turnStore;
  private final GroupService groups;
  private final GroupAgentContextService contexts;
  private final TaskRegistrationService taskRegistration;
  private final AgentTaskMutationService taskMutations;
  private final AgentChecklistActionService checklistActions;
  private final StoreInfoService storeInfo;
  private final AgentNotificationService notifications;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public List<TurnResponse> history(long groupId, long managerId, int limit) {
    groups.requireManager(groupId, managerId);
    return turnStore.history(groupId, limit).stream()
        .map(this::recoverStale)
        .map(this::response)
        .toList();
  }

  public TurnResponse turn(long groupId, long managerId, String requestId) {
    groups.requireManager(groupId, managerId);
    return response(recoverStale(turnStore.find(groupId, requestId)));
  }

  public ChatResponse chat(long groupId, long managerId, String requestId, String message) {
    GroupMember manager = groups.requireManager(groupId, managerId);
    AgentTurnStore.CreatedTurn created =
        turnStore.create(manager, requestId, message.strip());
    if (!created.created()) {
      AgentTurn existing = recoverStale(created.turn());
      if (!existing.getUserMessage().equals(message.strip()))
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
      return new ChatResponse(response(existing), false);
    }

    AgentTurn turn = created.turn();
    List<ExecutedTool> executed = new ArrayList<>();
    List<ToolActivity> activities = new ArrayList<>();
    try {
      List<AgentTurn> recent = turnStore.history(groupId, MODEL_HISTORY_TURNS + 1).stream()
          .filter(item -> !item.getId().equals(turn.getId()))
          .filter(item -> item.getAssistantMessage() != null)
          .toList();
      List<ConversationMessage> history = modelHistory(recent);
      int contextActivity =
          startActivity(
              activities,
              "context",
              "READ_CONTEXT",
              "그룹의 태스크와 매장 정보를 확인하고 있어요.");
      persistProgress(turn.getId(), executed, activities);
      AgentAiClient.Context context =
          contexts.build(groupId, managerId, manager, retrievalText(message, history));
      finishActivity(
          activities,
          contextActivity,
          "SUCCEEDED",
          "그룹의 태스크와 매장 정보를 확인했어요.");
      persistProgress(turn.getId(), executed, activities);
      AgentAiClient.Request planRequest =
          new AgentAiClient.Request(context, history, message.strip(), List.of());
      AgentAiClient.Response plan = ai.respond(planRequest);

      validatePlan(plan, context, 5, Set.of());
      List<AgentAiClient.ToolCall> attemptedCalls = new ArrayList<>();
      Map<String, AgentAiClient.ToolResult> resultsByCallId = new LinkedHashMap<>();
      executePlan(
          groupId,
          managerId,
          turn.getId(),
          context,
          plan,
          attemptedCalls,
          executed,
          activities,
          resultsByCallId);

      String recoveryAnswer = "";
      if (executed.stream().anyMatch(item -> !item.result().success())) {
        try {
          int recoveryContextActivity =
              startActivity(
                  activities,
                  "recovery-context",
                  "READ_CONTEXT",
                  "실행 결과와 최신 그룹 상태를 다시 확인하고 있어요.");
          persistProgress(turn.getId(), executed, activities);
          AgentAiClient.Context refreshedContext =
              contexts.build(groupId, managerId, manager, retrievalText(message, history));
          finishActivity(
              activities,
              recoveryContextActivity,
              "SUCCEEDED",
              "실행 결과와 최신 그룹 상태를 다시 확인했어요.");
          persistProgress(turn.getId(), executed, activities);
          List<AgentAiClient.ToolResult> firstResults =
              executed.stream().map(ExecutedTool::result).toList();
          AgentAiClient.Response recovery =
              ai.respond(
                  new AgentAiClient.Request(
                      refreshedContext, history, message.strip(), firstResults));
          int remainingBudget = 5 - attemptedCalls.size();
          validatePlan(
              recovery,
              refreshedContext,
              remainingBudget,
              attemptedCalls.stream()
                  .map(AgentAiClient.ToolCall::callId)
                  .collect(java.util.stream.Collectors.toSet()));
          rejectRepeatedSuccessfulActions(recovery.toolCalls(), attemptedCalls, executed);
          recoveryAnswer = recovery.message();
          executePlan(
              groupId,
              managerId,
              turn.getId(),
              refreshedContext,
              recovery,
              attemptedCalls,
              executed,
              activities,
              resultsByCallId);
        } catch (RuntimeException recoveryFailure) {
          interruptRunningActivities(activities);
          persistProgress(turn.getId(), executed, activities);
          log.warn(
              "Group agent recovery stopped groupId={} turnId={} reason={}",
              groupId,
              turn.getId(),
              recoveryFailure.getMessage());
          recoveryAnswer = "실패한 후속 작업은 자동으로 복구하지 못했습니다. 현재 상태를 확인해 주세요.";
        }
      }

      List<AgentAiClient.ToolResult> results =
          executed.stream().map(ExecutedTool::result).toList();
      String finalMessage = finalMessage(mergeAnswers(plan.message(), recoveryAnswer), results);

      List<NotificationCard> cards =
          executed.stream()
              .map(ExecutedTool::notificationCard)
              .filter(java.util.Objects::nonNull)
              .toList();
      AgentTurn completed =
          turnStore.complete(
              turn.getId(), finalMessage, cardsJson(cards), toolResultsJson(results));
      boolean mutated = executed.stream().anyMatch(item -> item.result().success());
      return new ChatResponse(response(completed), mutated);
    } catch (BusinessException exception) {
      turnStore.fail(
          turn.getId(),
          failureMessage(executed, exception.getMessage()),
          activitiesJson(interruptRunningActivities(activities)));
      throw exception;
    } catch (RuntimeException exception) {
      log.error("Group agent failed groupId={} turnId={}", groupId, turn.getId(), exception);
      turnStore.fail(
          turn.getId(),
          failureMessage(executed, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),
          activitiesJson(interruptRunningActivities(activities)));
      throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }
  }

  private List<ConversationMessage> modelHistory(List<AgentTurn> turns) {
    List<ConversationMessage> history = new ArrayList<>(turns.size() * 2);
    for (AgentTurn turn : turns) {
      history.add(new ConversationMessage("USER", turn.getUserMessage()));
      history.add(new ConversationMessage("ASSISTANT", turn.getAssistantMessage()));
    }
    return history;
  }

  private String retrievalText(String message, List<ConversationMessage> history) {
    StringBuilder text = new StringBuilder(message.strip());
    history.stream()
        .skip(Math.max(0, history.size() - 6))
        .forEach(item -> text.append('\n').append(item.content()));
    return text.toString();
  }

  private void validatePlan(
      AgentAiClient.Response plan,
      AgentAiClient.Context context,
      int toolCallBudget,
      Set<String> reservedCallIds) {
    if (plan == null
        || plan.message() == null
        || plan.message().length() > 4_000
        || plan.toolCalls() == null
        || plan.toolCalls().size() > toolCallBudget
        || (plan.toolCalls().isEmpty() && plan.message().isBlank()))
      throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    Set<String> previousCallIds = new HashSet<>();
    List<AgentAiClient.ToolCall> validatedCalls = new ArrayList<>();
    int storeReplacements = 0;
    for (AgentAiClient.ToolCall call : plan.toolCalls()) {
      if (call == null
          || call.callId() == null
          || call.callId().isBlank()
          || call.callId().length() > 40
          || reservedCallIds.contains(call.callId())
          || !previousCallIds.add(call.callId())
          || call.tool() == null
          || call.dependsOnCallIds() == null
          || call.dependsOnCallIds().size() > 4
          || !previousCallIds.containsAll(call.dependsOnCallIds())
          || call.dependsOnCallIds().contains(call.callId())
          || call.decisionBasis() == null
          || call.decisionBasis().isBlank()
          || call.decisionBasis().strip().length() > 300) {
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
      }
      evidenceLabels(context, call.evidenceRefs());
      if (validatedCalls.stream().anyMatch(previous -> sameAction(previous, call)))
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
      validatedCalls.add(call);
      if ("REPLACE_STORE_INFO".equalsIgnoreCase(call.tool()) && ++storeReplacements > 1)
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }
  }

  private void executePlan(
      long groupId,
      long managerId,
      long turnId,
      AgentAiClient.Context context,
      AgentAiClient.Response plan,
      List<AgentAiClient.ToolCall> attemptedCalls,
      List<ExecutedTool> executed,
      List<ToolActivity> activities,
      Map<String, AgentAiClient.ToolResult> resultsByCallId) {
    for (AgentAiClient.ToolCall call : plan.toolCalls()) {
      List<String> evidence = evidenceLabels(context, call.evidenceRefs());
      int activityIndex =
          startActivity(
              activities,
              call.callId(),
              call.tool(),
              toolActivityMessage(call.tool(), "RUNNING"));
      persistProgress(turnId, executed, activities);
      boolean dependencyFailed =
          call.dependsOnCallIds().stream()
              .map(resultsByCallId::get)
              .anyMatch(result -> result == null || !result.success());
      ExecutedTool result =
          dependencyFailed
              ? failedTool(
                  call.callId(),
                  call.tool(),
                  "선행 작업이 완료되지 않아 실행하지 않았습니다.",
                  call.decisionBasis(),
                  evidence,
                  null)
              : executeSafely(groupId, managerId, call, evidence);
      attemptedCalls.add(call);
      executed.add(result);
      resultsByCallId.put(call.callId(), result.result());
      finishActivity(
          activities,
          activityIndex,
          result.result().success() ? "SUCCEEDED" : "FAILED",
          toolActivityMessage(
              call.tool(), result.result().success() ? "SUCCEEDED" : "FAILED"));
      persistProgress(turnId, executed, activities);
    }
  }

  private ExecutedTool executeSafely(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    try {
      return execute(groupId, managerId, call, evidence);
    } catch (BusinessException exception) {
      return failedTool(
          call.callId(),
          call.tool(),
          exception.getMessage(),
          call.decisionBasis(),
          evidence,
          null);
    }
  }

  private void rejectRepeatedSuccessfulActions(
      List<AgentAiClient.ToolCall> recoveryCalls,
      List<AgentAiClient.ToolCall> attemptedCalls,
      List<ExecutedTool> executed) {
    for (AgentAiClient.ToolCall recoveryCall : recoveryCalls) {
      for (int index = 0; index < attemptedCalls.size(); index++) {
        if (executed.get(index).result().success()
            && sameSuccessfulEffect(recoveryCall, attemptedCalls.get(index))) {
          throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
      }
    }
  }

  private boolean sameAction(AgentAiClient.ToolCall left, AgentAiClient.ToolCall right) {
    return left.tool().equalsIgnoreCase(right.tool())
        && Objects.equals(left.taskId(), right.taskId())
        && Objects.equals(left.runId(), right.runId())
        && Objects.equals(left.checklistId(), right.checklistId())
        && Objects.equals(left.title(), right.title())
        && Objects.equals(left.sourceMessage(), right.sourceMessage())
        && Objects.equals(left.workerId(), right.workerId())
        && Objects.equals(left.dueAt(), right.dueAt())
        && Objects.equals(left.notifyOnCompletion(), right.notifyOnCompletion())
        && Objects.equals(left.active(), right.active())
        && Objects.equals(left.checklists(), right.checklists())
        && Objects.equals(left.storeInfo(), right.storeInfo())
        && Objects.equals(left.removedStoreInfoIds(), right.removedStoreInfoIds())
        && Objects.equals(left.recipientMemberIds(), right.recipientMemberIds())
        && Objects.equals(left.notificationMessage(), right.notificationMessage());
  }

  private boolean sameSuccessfulEffect(
      AgentAiClient.ToolCall recovery, AgentAiClient.ToolCall successful) {
    if (!recovery.tool().equalsIgnoreCase(successful.tool())) return false;
    return switch (recovery.tool().toUpperCase(Locale.ROOT)) {
      case "CREATE_TASK" ->
          Objects.equals(recovery.title(), successful.title())
              && Objects.equals(recovery.workerId(), successful.workerId());
      case "UPDATE_TASK" -> Objects.equals(recovery.taskId(), successful.taskId());
      case "DELETE_TASK" -> Objects.equals(recovery.taskId(), successful.taskId());
      case "COMPLETE_CHECKLIST" ->
          Objects.equals(recovery.taskId(), successful.taskId())
              && Objects.equals(recovery.runId(), successful.runId())
              && Objects.equals(recovery.checklistId(), successful.checklistId());
      case "REPLACE_STORE_INFO" -> true;
      case "SEND_NOTIFICATION" ->
          Objects.equals(recovery.notificationMessage(), successful.notificationMessage())
              && new HashSet<>(recovery.recipientMemberIds())
                  .equals(new HashSet<>(successful.recipientMemberIds()));
      default -> false;
    };
  }

  private List<String> evidenceLabels(
      AgentAiClient.Context context, List<String> references) {
    if (references == null || references.isEmpty() || references.size() > 10)
      throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    Map<String, String> available = new LinkedHashMap<>();
    available.put("USER_REQUEST", "이번 요청");
    available.put("CURRENT_TIME", "현재 시각 " + context.currentDateTime());
    context.members().forEach(
        member ->
            available.put(
                "MEMBER:" + member.memberId(), "구성원 ‘" + member.nickname() + "’"));
    context.storeInfo().stream()
        .filter(item -> item.storeInfoId() != null)
        .forEach(
            item ->
                available.put(
                    "STORE_INFO:" + item.storeInfoId(), "매장 정보 ‘" + item.title() + "’"));
    context.tasks().forEach(
        task -> {
          available.put("TASK:" + task.taskId(), "태스크 ‘" + task.title() + "’");
          available.put("TASK_RUN:" + task.runId(), "실행 회차 ‘" + task.title() + "’");
        });

    LinkedHashMap<String, String> labels = new LinkedHashMap<>();
    for (String reference : references) {
      String label = available.get(reference);
      if (label == null) throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
      labels.putIfAbsent(reference, label);
    }
    return List.copyOf(labels.values());
  }

  private ExecutedTool execute(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    if (call == null
        || call.callId() == null
        || call.callId().isBlank()
        || call.callId().length() > 40
        || call.tool() == null) {
      return failedTool(
          "unknown",
          "UNKNOWN",
          "AI가 올바르지 않은 도구 요청을 만들었습니다.",
          null,
          List.of(),
          null);
    }
    String tool = call.tool().toUpperCase(Locale.ROOT);
    try {
      return switch (tool) {
        case "CREATE_TASK" -> createTask(groupId, managerId, call, evidence);
        case "UPDATE_TASK" -> updateTask(groupId, managerId, call, evidence);
        case "DELETE_TASK" -> deleteTask(groupId, managerId, call, evidence);
        case "COMPLETE_CHECKLIST" -> completeChecklist(groupId, managerId, call, evidence);
        case "REPLACE_STORE_INFO" -> replaceStoreInfo(groupId, managerId, call, evidence);
        case "SEND_NOTIFICATION" -> sendNotification(groupId, managerId, call, evidence);
        default ->
            failedTool(
                call.callId(),
                tool,
                "지원하지 않는 도구입니다.",
                call.decisionBasis(),
                evidence,
                null);
      };
    } catch (BusinessException exception) {
      NotificationCard card =
          "SEND_NOTIFICATION".equals(tool)
              ? new NotificationCard(call.callId(), false, List.of(), call.notificationMessage(), exception.getMessage())
              : null;
      return failedTool(
          call.callId(),
          tool,
          exception.getMessage(),
          call.decisionBasis(),
          evidence,
          card);
    } catch (DateTimeParseException exception) {
      return failedTool(
          call.callId(),
          tool,
          "마감 일시 형식이 올바르지 않습니다.",
          call.decisionBasis(),
          evidence,
          null);
    } catch (RuntimeException exception) {
      log.error(
          "Agent tool failed groupId={} tool={} callId={}",
          groupId,
          tool,
          call.callId(),
          exception);
      NotificationCard card =
          "SEND_NOTIFICATION".equals(tool)
              ? new NotificationCard(
                  call.callId(),
                  false,
                  List.of(),
                  call.notificationMessage(),
                  "서버 오류로 알림을 예약하지 못했습니다.")
              : null;
      return failedTool(
          call.callId(),
          tool,
          "서버 오류로 작업을 처리하지 못했습니다.",
          call.decisionBasis(),
          evidence,
          card);
    }
  }

  private ExecutedTool createTask(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    require(call.title(), call.sourceMessage(), call.workerId(), call.dueAt());
    validateText(call.title(), 80);
    validateText(call.sourceMessage(), 2_000);
    if (call.checklists() == null
        || call.checklists().isEmpty()
        || call.checklists().size() > 20)
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    call.checklists().forEach(this::validateChecklist);
    boolean notifyOnCompletion = call.notifyOnCompletion() == null || call.notifyOnCompletion();
    var created =
        taskRegistration.create(
            groupId,
            managerId,
            call.title(),
            call.sourceMessage(),
            call.workerId(),
            OffsetDateTime.parse(call.dueAt()),
            java.util.stream.IntStream.range(0, call.checklists().size())
                .mapToObj(
                    index -> {
                      AgentAiClient.Checklist item = call.checklists().get(index);
                      return new TaskRegistrationService.ChecklistCommand(
                          index + 1,
                          item.title(),
                          item.instruction(),
                          item.completionType(),
                          item.rule(),
                          null);
                    })
                .toList(),
            List.of(),
            notifyOnCompletion);
    String summary =
        "‘"
            + created.title()
            + "’ 태스크를 "
            + created.worker().nickname()
            + "에게 생성했습니다. 완료 알림은 "
            + (notifyOnCompletion ? "켜짐" : "꺼짐")
            + "입니다.";
    return successTool(call, summary, evidence, null);
  }

  private ExecutedTool updateTask(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    if (call.taskId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    OffsetDateTime dueAt = call.dueAt() == null ? null : OffsetDateTime.parse(call.dueAt());
    var updated =
        taskMutations.update(
            groupId,
            managerId,
            call.taskId(),
            call.title(),
            call.sourceMessage(),
            call.workerId(),
            dueAt,
            call.notifyOnCompletion(),
            call.active());
    String summary = "‘" + updated.title() + "’ 태스크를 수정했습니다.";
    return successTool(call, summary, evidence, null);
  }

  private ExecutedTool deleteTask(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    if (call.taskId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    var deactivated = taskMutations.deactivate(groupId, managerId, call.taskId());
    return successTool(
        call,
        "‘" + deactivated.title() + "’ 태스크를 삭제했습니다. 기존 수행 이력은 보존됩니다.",
        evidence,
        null);
  }

  private ExecutedTool completeChecklist(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    require(call.taskId(), call.runId(), call.checklistId());
    var completed =
        checklistActions.complete(
            groupId,
            managerId,
            call.taskId(),
            call.runId(),
            call.checklistId());
    return successTool(call, "‘" + completed.title() + "’ 항목을 완료 처리했습니다.", evidence, null);
  }

  private ExecutedTool replaceStoreInfo(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    if (call.storeInfo() == null || call.removedStoreInfoIds() == null)
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    var replaced =
        storeInfo.replaceAll(
            groupId,
            managerId,
            call.storeInfo().stream()
                .map(
                    item ->
                        new StoreInfoService.ReplaceCommand(
                            item.storeInfoId(),
                            item.category(),
                            item.title(),
                            item.content()))
                .toList(),
            call.removedStoreInfoIds());
    return successTool(
        call,
        "매장 정보 문서를 " + replaced.size() + "개 항목으로 교체했습니다.",
        evidence,
        null);
  }

  private ExecutedTool sendNotification(
      long groupId,
      long managerId,
      AgentAiClient.ToolCall call,
      List<String> evidence) {
    var queued =
        notifications.queue(
            groupId, managerId, call.recipientMemberIds(), call.notificationMessage());
    List<Recipient> recipients =
        queued.stream().map(item -> new Recipient(item.memberId(), item.nickname())).toList();
    NotificationCard card =
        new NotificationCard(call.callId(), true, recipients, call.notificationMessage(), null);
    String recipientNames =
        recipients.stream()
            .map(Recipient::nickname)
            .collect(java.util.stream.Collectors.joining(", "));
    return successTool(
        call, recipientNames + "에게 알림 전송을 예약했습니다.", evidence, card);
  }

  private void require(Object... values) {
    for (Object value : values)
      if (value == null || value instanceof String text && text.isBlank())
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
  }

  private void validateChecklist(AgentAiClient.Checklist checklist) {
    if (checklist == null || checklist.completionType() == null)
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    validateText(checklist.title(), 80);
    validateText(checklist.instruction(), 500);
    if (checklist.completionType() == com.ktb.hackathon.team11.ai.CompletionType.PHOTO) {
      validateText(checklist.rule(), 1_000);
    } else if (checklist.rule() != null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private void validateText(String value, int maxLength) {
    if (value == null || value.isBlank() || value.strip().length() > maxLength)
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
  }

  private ExecutedTool successTool(
      AgentAiClient.ToolCall call,
      String summary,
      List<String> evidence,
      NotificationCard card) {
    return new ExecutedTool(
        new AgentAiClient.ToolResult(
            call.callId(),
            call.tool(),
            true,
            summary,
            call.decisionBasis(),
            evidence),
        card);
  }

  private ExecutedTool failedTool(
      String callId,
      String tool,
      String summary,
      String decisionBasis,
      List<String> evidence,
      NotificationCard card) {
    return new ExecutedTool(
        new AgentAiClient.ToolResult(
            callId, tool, false, summary, decisionBasis, evidence),
        card);
  }

  private String deterministicReport(List<AgentAiClient.ToolResult> results) {
    return results.stream()
        .map(
            result -> {
              String report =
                  (result.success() ? "완료: " : "처리 실패: ") + result.summary();
              if (result.decisionBasis() == null || result.decisionBasis().isBlank()) return report;
              String sources =
                  result.evidence().isEmpty()
                      ? ""
                      : " (" + String.join(", ", result.evidence()) + ")";
              return report + "\nAI가 참고한 정보: " + result.decisionBasis() + sources;
            })
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private String finalMessage(String answer, List<AgentAiClient.ToolResult> results) {
    String normalizedAnswer = answer == null ? "" : answer.strip();
    if (results.isEmpty()) return normalizedAnswer;
    String report = deterministicReport(results);
    return normalizedAnswer.isBlank() ? report : normalizedAnswer + "\n\n" + report;
  }

  private String mergeAnswers(String first, String second) {
    String normalizedFirst = first == null ? "" : first.strip();
    String normalizedSecond = second == null ? "" : second.strip();
    if (normalizedFirst.isBlank()) return normalizedSecond;
    if (normalizedSecond.isBlank() || normalizedFirst.equals(normalizedSecond)) return normalizedFirst;
    return normalizedFirst + "\n\n" + normalizedSecond;
  }

  private String failureMessage(List<ExecutedTool> executed, String failure) {
    if (executed.isEmpty()) return failure;
    List<AgentAiClient.ToolResult> results =
        executed.stream().map(ExecutedTool::result).toList();
    return deterministicReport(results)
        + "\n\n후속 처리가 중단되었습니다: "
        + failure
        + " 현재 상태를 확인한 뒤 다시 요청해 주세요.";
  }

  private int startActivity(
      List<ToolActivity> activities,
      String callId,
      String tool,
      String message) {
    activities.add(new ToolActivity(callId, tool, "RUNNING", message));
    return activities.size() - 1;
  }

  private void finishActivity(
      List<ToolActivity> activities,
      int index,
      String status,
      String message) {
    ToolActivity current = activities.get(index);
    activities.set(index, new ToolActivity(current.callId(), current.tool(), status, message));
  }

  private List<ToolActivity> interruptRunningActivities(List<ToolActivity> activities) {
    for (int index = 0; index < activities.size(); index++) {
      ToolActivity activity = activities.get(index);
      if (!"RUNNING".equals(activity.status())) continue;
      finishActivity(
          activities,
          index,
          "FAILED",
          toolActivityMessage(activity.tool(), "FAILED"));
    }
    return activities;
  }

  private String toolActivityMessage(String tool, String status) {
    String normalizedTool = tool.toUpperCase(Locale.ROOT);
    return switch (normalizedTool + ":" + status) {
      case "READ_CONTEXT:RUNNING" -> "그룹 정보를 확인하고 있어요.";
      case "READ_CONTEXT:SUCCEEDED" -> "그룹 정보를 확인했어요.";
      case "READ_CONTEXT:FAILED" -> "그룹 정보를 확인하지 못했어요.";
      case "CREATE_TASK:RUNNING" -> "새 태스크를 만들고 있어요.";
      case "CREATE_TASK:SUCCEEDED" -> "새 태스크를 만들었어요.";
      case "CREATE_TASK:FAILED" -> "태스크를 만들지 못했어요.";
      case "UPDATE_TASK:RUNNING" -> "태스크 내용을 수정하고 있어요.";
      case "UPDATE_TASK:SUCCEEDED" -> "태스크 내용을 수정했어요.";
      case "UPDATE_TASK:FAILED" -> "태스크를 수정하지 못했어요.";
      case "DELETE_TASK:RUNNING" -> "태스크를 삭제하고 있어요.";
      case "DELETE_TASK:SUCCEEDED" -> "태스크를 삭제했어요.";
      case "DELETE_TASK:FAILED" -> "태스크를 삭제하지 못했어요.";
      case "COMPLETE_CHECKLIST:RUNNING" -> "체크 항목을 완료 처리하고 있어요.";
      case "COMPLETE_CHECKLIST:SUCCEEDED" -> "체크 항목을 완료 처리했어요.";
      case "COMPLETE_CHECKLIST:FAILED" -> "체크 항목을 완료하지 못했어요.";
      case "REPLACE_STORE_INFO:RUNNING" -> "매장 정보를 수정하고 있어요.";
      case "REPLACE_STORE_INFO:SUCCEEDED" -> "매장 정보를 수정했어요.";
      case "REPLACE_STORE_INFO:FAILED" -> "매장 정보를 수정하지 못했어요.";
      case "SEND_NOTIFICATION:RUNNING" -> "알바생에게 보낼 알림을 준비하고 있어요.";
      case "SEND_NOTIFICATION:SUCCEEDED" -> "알바생 알림 전송을 예약했어요.";
      case "SEND_NOTIFICATION:FAILED" -> "알바생 알림을 예약하지 못했어요.";
      default ->
          switch (status) {
            case "RUNNING" -> "요청한 작업을 처리하고 있어요.";
            case "SUCCEEDED" -> "요청한 작업을 처리했어요.";
            default -> "요청한 작업을 처리하지 못했어요.";
          };
    };
  }

  private void persistProgress(
      long turnId,
      List<ExecutedTool> executed,
      List<ToolActivity> activities) {
    turnStore.progress(
        turnId,
        cardsJson(
            executed.stream()
                .map(ExecutedTool::notificationCard)
                .filter(Objects::nonNull)
                .toList()),
        toolResultsJson(executed.stream().map(ExecutedTool::result).toList()),
        activitiesJson(activities));
  }

  private AgentTurn recoverStale(AgentTurn turn) {
    if (turn.getStatus() != AgentTurnStatus.PROCESSING) return turn;
    // JPA 감사 시각은 JVM 기본 시간대의 LocalDateTime이므로 서울 LocalDateTime과 직접 비교하지 않는다.
    // 마지막 진행 저장 시각을 같은 Instant 축으로 바꿔 실제로 멈춘 요청만 복구한다.
    var lastProgressAt =
        turn.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant();
    if (!lastProgressAt.isBefore(clock.instant().minus(PROCESSING_STALE_AFTER))) return turn;
    List<AgentAiClient.ToolResult> partialResults = toolResults(turn.getToolResultsJson());
    String message =
        partialResults.isEmpty()
            ? INTERRUPTED_MESSAGE
            : deterministicReport(partialResults) + "\n\n" + INTERRUPTED_MESSAGE;
    List<ToolActivity> interrupted =
        interruptRunningActivities(activities(turn.getToolActivitiesJson()));
    return turnStore.fail(turn.getId(), message, activitiesJson(interrupted));
  }

  private List<AgentAiClient.ToolResult> toolResults(String json) {
    try {
      return List.of(objectMapper.readValue(json, AgentAiClient.ToolResult[].class));
    } catch (JacksonException exception) {
      log.warn("Invalid agent tool result JSON");
      return List.of();
    }
  }

  private String cardsJson(List<NotificationCard> cards) {
    return json(cards, "notification cards");
  }

  private String toolResultsJson(List<AgentAiClient.ToolResult> results) {
    return json(results, "tool results");
  }

  private String activitiesJson(List<ToolActivity> activities) {
    return json(activities, "tool activities");
  }

  private String json(Object value, String label) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      log.error("Agent {} could not be serialized", label, exception);
      return "[]";
    }
  }

  private List<NotificationCard> cards(String json) {
    try {
      return List.of(objectMapper.readValue(json, NotificationCard[].class));
    } catch (JacksonException exception) {
      log.warn("Invalid agent notification card JSON");
      return List.of();
    }
  }

  private List<ToolActivity> activities(String json) {
    try {
      return new ArrayList<>(List.of(objectMapper.readValue(json, ToolActivity[].class)));
    } catch (JacksonException exception) {
      log.warn("Invalid agent tool activity JSON");
      return new ArrayList<>();
    }
  }

  private TurnResponse response(AgentTurn turn) {
    return new TurnResponse(
        turn.getId(),
        turn.getRequestId(),
        turn.getManager().getNickname(),
        turn.getUserMessage(),
        turn.getAssistantMessage(),
        turn.getStatus(),
        activities(turn.getToolActivitiesJson()),
        cards(turn.getNotificationCardsJson()),
        turn.getCreatedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(SERVICE_ZONE)
            .toOffsetDateTime());
  }

  private record ExecutedTool(AgentAiClient.ToolResult result, NotificationCard notificationCard) {}

  public record Recipient(long memberId, String nickname) {}

  public record NotificationCard(
      String callId,
      boolean success,
      List<Recipient> recipients,
      String message,
      String errorMessage) {}

  public record ToolActivity(String callId, String tool, String status, String message) {}

  public record TurnResponse(
      long turnId,
      String requestId,
      String managerNickname,
      String userMessage,
      String assistantMessage,
      AgentTurnStatus status,
      List<ToolActivity> activities,
      List<NotificationCard> notificationCards,
      OffsetDateTime createdAt) {}

  public record ChatResponse(TurnResponse turn, boolean mutated) {}
}
