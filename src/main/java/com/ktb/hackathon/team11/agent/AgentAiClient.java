package com.ktb.hackathon.team11.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.hackathon.team11.ai.CompletionType;
import java.util.List;

public interface AgentAiClient {
  Response respond(Request request);

  record Request(
      Context context,
      List<ConversationMessage> history,
      String message,
      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      List<ToolResult> toolResults) {}

  record Context(
      long groupId,
      String groupName,
      String currentDateTime,
      int memberTotalCount,
      List<Member> members,
      int taskTotalCount,
      List<Task> tasks,
      List<TaskDetail> taskDetails,
      List<StoreInfoItem> storeInfo) {}

  record Member(long memberId, String nickname, String role) {}

  record Task(
      long taskId,
      String runId,
      String title,
      Long workerId,
      String workerNickname,
      String dueAt,
      String status,
      int itemCount,
      int completedItemCount,
      int progress,
      boolean notifyOnCompletion) {}

  record TaskDetail(
      long taskId,
      String runId,
      String title,
      String sourceMessage,
      Long workerId,
      String workerNickname,
      String dueAt,
      String status,
      boolean notifyOnCompletion,
      List<TaskDetailChecklist> checklists) {}

  record TaskDetailChecklist(
      long checklistId,
      String title,
      String instruction,
      CompletionType completionType,
      String rule,
      boolean performed) {}

  record StoreInfoItem(Long storeInfoId, String category, String title, String content) {}

  record ConversationMessage(String role, String content) {}

  record ToolResult(
      String callId,
      String tool,
      boolean success,
      String summary,
      String decisionBasis,
      List<String> evidence) {}

  record Response(String message, List<ToolCall> toolCalls) {}

  record ToolCall(
      String callId,
      String tool,
      List<String> dependsOnCallIds,
      List<String> evidenceRefs,
      String decisionBasis,
      Long taskId,
      String runId,
      Long checklistId,
      String title,
      String sourceMessage,
      Long workerId,
      String dueAt,
      Boolean notifyOnCompletion,
      Boolean active,
      List<Checklist> checklists,
      List<StoreInfoItem> storeInfo,
      List<Long> removedStoreInfoIds,
      List<Long> recipientMemberIds,
      String notificationMessage) {}

  record Checklist(
      String title, String instruction, CompletionType completionType, String rule) {}
}
