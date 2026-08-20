package com.ktb.hackathon.team11.agent;

import com.ktb.hackathon.team11.group.GroupMember;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.store.StoreInfoService;
import com.ktb.hackathon.team11.task.TaskQueryService;
import com.ktb.hackathon.team11.task.TaskTemplateRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupAgentContextService {
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
  private static final int MAX_CONTEXT_TASKS = 100;
  private static final int MAX_TASK_DETAILS = 5;
  private static final Set<String> GENERIC_TERMS =
      Set.of(
          "태스크",
          "업무",
          "작업",
          "오늘",
          "내일",
          "완료",
          "확인",
          "만들어",
          "수정",
          "알려줘",
          "해줘");

  private final GroupService groups;
  private final TaskQueryService taskQueries;
  private final TaskTemplateRepository taskTemplates;
  private final StoreInfoService storeInfo;
  private final Clock clock;

  public AgentAiClient.Context build(
      long groupId, long managerId, GroupMember manager, String retrievalText) {
    List<GroupMember> memberships = groups.membersOf(groupId, managerId);
    List<AgentAiClient.Member> members =
        memberships.stream()
            .limit(100)
            .map(
                membership ->
                    new AgentAiClient.Member(
                        membership.getMember().getId(),
                        membership.getMember().getNickname(),
                        membership.getGroupRole().name()))
            .toList();

    TaskQueryService.TaskListResponse taskPage =
        taskQueries.list(groupId, managerId, 0, MAX_CONTEXT_TASKS, null);
    List<AgentAiClient.Task> tasks =
        taskPage.items().stream()
            .map(
                task ->
                    new AgentAiClient.Task(
                        task.taskId(),
                        task.runId(),
                        task.title(),
                        task.workerId(),
                        task.workerNickname(),
                        task.dueAt() == null ? null : task.dueAt().toString(),
                        task.status().name(),
                        task.itemCount(),
                        task.completedItemCount(),
                        task.progress(),
                        task.notifyOnCompletion()))
            .toList();

    Map<Long, String> taskSourceMessages =
        taskTemplates
            .findAllById(
                taskPage.items().stream()
                    .map(TaskQueryService.TaskSummary::taskId)
                    .distinct()
                    .toList())
            .stream()
            .collect(
                Collectors.toMap(
                    template -> template.getId(), template -> template.getSourceMessage()));
    List<String> relevantRunIds =
        selectRelevantRunIds(taskPage.items(), retrievalText, taskSourceMessages);
    List<AgentAiClient.TaskDetail> taskDetails =
        taskQueries.agentDetails(groupId, managerId, relevantRunIds).stream()
            .map(
                detail ->
                    new AgentAiClient.TaskDetail(
                        detail.taskId(),
                        detail.runId(),
                        detail.title(),
                        detail.sourceMessage(),
                        detail.workerId(),
                        detail.workerNickname(),
                        detail.dueAt() == null ? null : detail.dueAt().toString(),
                        detail.status().name(),
                        detail.notifyOnCompletion(),
                        detail.checklists().stream()
                            .map(
                                checklist ->
                                    new AgentAiClient.TaskDetailChecklist(
                                        checklist.checklistId(),
                                        checklist.title(),
                                        checklist.instruction(),
                                        checklist.completionType(),
                                        checklist.rule(),
                                        checklist.performed()))
                            .toList()))
            .toList();

    List<AgentAiClient.StoreInfoItem> information =
        storeInfo.list(groupId, managerId).stream()
            .map(
                item ->
                    new AgentAiClient.StoreInfoItem(
                        item.storeInfoId(),
                        item.category().name(),
                        item.title(),
                        item.content()))
            .toList();
    String now =
        ZonedDateTime.now(clock)
            .withZoneSameInstant(SERVICE_ZONE)
            .toOffsetDateTime()
            .toString();
    return new AgentAiClient.Context(
        groupId,
        manager.getGroup().getName(),
        now,
        memberships.size(),
        members,
        taskPage.totalCount(),
        tasks,
        taskDetails,
        information);
  }

  private List<String> selectRelevantRunIds(
      List<TaskQueryService.TaskSummary> tasks,
      String retrievalText,
      Map<Long, String> taskSourceMessages) {
    if (tasks.isEmpty()) return List.of();
    Set<String> terms = terms(retrievalText);
    List<ScoredTask> scored =
        tasks.stream()
            .map(
                task ->
                    new ScoredTask(
                        task,
                        relevance(
                            task,
                            taskSourceMessages.get(task.taskId()),
                            retrievalText,
                            terms)))
            .filter(item -> item.score() > 0)
            .sorted(Comparator.comparingInt(ScoredTask::score).reversed())
            .limit(MAX_TASK_DETAILS)
            .toList();
    if (!scored.isEmpty())
      return scored.stream().map(item -> item.task().runId()).toList();

    // 지시가 "그거"처럼 앞 대화를 가리킬 때만 최신 후보를 제공한다. 일반 요청에 무관한
    // 태스크를 넣으면 담당자나 체크리스트를 잘못 재사용할 수 있으므로 빈 결과가 더 안전하다.
    String normalized = normalize(retrievalText);
    if (List.of("그거", "그것", "아까", "첫번째", "첫 번째").stream()
        .anyMatch(normalized::contains))
      return tasks.stream().limit(3).map(TaskQueryService.TaskSummary::runId).toList();
    return List.of();
  }

  private int relevance(
      TaskQueryService.TaskSummary task,
      String sourceMessage,
      String retrievalText,
      Set<String> terms) {
    String query = normalize(retrievalText);
    String title = normalize(task.title());
    String worker = normalize(task.workerNickname());
    String source = normalize(sourceMessage);
    int score = 0;
    if (!query.isBlank() && query.contains(normalize(task.runId()))) score += 200;
    if (!query.isBlank()
        && Arrays.asList(query.split("\\s+")).contains(Long.toString(task.taskId()))) score += 100;
    for (String term : terms) {
      if (title.contains(term)) score += 12;
      if (source.contains(term)) score += 6;
      if (worker.contains(term)) score += 4;
    }
    return score;
  }

  private Set<String> terms(String value) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    Arrays.stream(normalize(value).split("\\s+"))
        .filter(term -> term.length() >= 2)
        .filter(term -> !GENERIC_TERMS.contains(term))
        .limit(30)
        .forEach(
            term -> {
              result.add(term);
              String stem = stripParticle(term);
              if (stem.length() >= 2 && !GENERIC_TERMS.contains(stem)) result.add(stem);
            });
    return result;
  }

  private String stripParticle(String value) {
    for (String suffix : List.of("에서", "에게", "으로", "까지", "부터", "을", "를", "은", "는", "이", "가", "에"))
      if (value.endsWith(suffix) && value.length() > suffix.length() + 1)
        return value.substring(0, value.length() - suffix.length());
    return value;
  }

  private String normalize(String value) {
    if (value == null) return "";
    return value.toLowerCase(Locale.ROOT)
        .replace("오프닝", "오픈")
        .replace("클로징", "마감")
        .replace("테스크", "태스크")
        .replaceAll("[^0-9a-z가-힣]+", " ")
        .strip();
  }

  private record ScoredTask(TaskQueryService.TaskSummary task, int score) {}
}
