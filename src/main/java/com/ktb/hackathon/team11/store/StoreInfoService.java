package com.ktb.hackathon.team11.store;

import com.ktb.hackathon.team11.ai.StoreInfoAnswerClient;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.*;
import com.ktb.hackathon.team11.member.Member;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreInfoService {
  private static final int MAX_INFORMATION_LENGTH = 60_000;
  private static final String EMPTY_ANSWER = "아직 등록된 매장 정보가 없어요. 매니저에게 문의해 주세요.";
  private final StoreInfoRepository infos;
  private final GroupService groups;
  private final StoreInfoAnswerClient aiClient;

  @Transactional(readOnly = true)
  public List<Response> list(long groupId, long requesterId) {
    groups.requireGroup(groupId);
    groups.requireMember(groupId, requesterId);
    return infos.findAllByGroupIdOrderByCategoryAscIdAsc(groupId).stream().map(Response::from).toList();
  }

  @Transactional
  public Response create(long groupId, long managerId, String category, String title, String content) {
    WorkGroup group = groups.requireGroup(groupId);
    Member manager = manager(groups, groupId, managerId);
    validateLength(title, content);
    StoreInfo info = new StoreInfo(group, manager, parseCategory(category), title, content);
    ensureLimit(groupId, info, null);
    return Response.from(infos.save(info));
  }

  @Transactional
  public Response update(long groupId, long storeInfoId, long managerId, String category, String title, String content) {
    groups.requireManager(groupId, managerId);
    StoreInfo info = infos.findByIdAndGroupId(storeInfoId, groupId)
        .orElseThrow(() -> new BusinessException(ErrorCode.STORE_INFO_NOT_FOUND));
    validateLength(title, content);
    info.update(parseCategory(category), title, content);
    ensureLimit(groupId, info, info);
    return Response.from(info);
  }

  @Transactional
  public void delete(long groupId, long storeInfoId, long managerId) {
    groups.requireManager(groupId, managerId);
    StoreInfo info = infos.findByIdAndGroupId(storeInfoId, groupId)
        .orElseThrow(() -> new BusinessException(ErrorCode.STORE_INFO_NOT_FOUND));
    infos.delete(info);
  }

  @Transactional
  public List<Response> replaceAll(
      long groupId,
      long managerId,
      List<ReplaceCommand> commands,
      List<Long> removedStoreInfoIds) {
    WorkGroup group = groups.requireGroup(groupId);
    Member manager = manager(groups, groupId, managerId);
    if (commands == null
        || commands.size() > 100
        || removedStoreInfoIds == null
        || removedStoreInfoIds.size() > 100)
      throw new BusinessException(ErrorCode.INVALID_STORE_INFO_INPUT);

    List<StoreInfo> existing = infos.lockAllByGroupId(groupId);
    Set<Long> currentIds =
        existing.stream()
            .map(StoreInfo::getId)
            .collect(java.util.stream.Collectors.toSet());
    List<Long> retainedIds =
        commands.stream()
            .filter(Objects::nonNull)
            .map(ReplaceCommand::storeInfoId)
            .filter(Objects::nonNull)
            .toList();
    Set<Long> retained = new HashSet<>(retainedIds);
    Set<Long> removed = new HashSet<>(removedStoreInfoIds);
    Set<Long> accountedFor = new HashSet<>(retained);
    accountedFor.addAll(removed);
    if (retained.size() != retainedIds.size()
        || removed.size() != removedStoreInfoIds.size()
        || !Collections.disjoint(retained, removed)
        || !currentIds.equals(accountedFor)) {
      throw new BusinessException(ErrorCode.STORE_INFO_REPLACEMENT_CONFLICT);
    }

    Map<Long, StoreInfo> existingById =
        existing.stream()
            .collect(java.util.stream.Collectors.toMap(StoreInfo::getId, info -> info));
    List<StoreInfo> replacements = new ArrayList<>(commands.size());
    for (ReplaceCommand command : commands) {
      if (command == null)
        throw new BusinessException(ErrorCode.INVALID_STORE_INFO_INPUT);
      validateLength(command.title(), command.content());
      StoreInfo replacement;
      if (command.storeInfoId() == null) {
        replacement =
            new StoreInfo(
                group,
                manager,
                parseCategory(command.category()),
                command.title(),
                command.content());
      } else {
        replacement = existingById.get(command.storeInfoId());
        replacement.update(
            parseCategory(command.category()), command.title(), command.content());
      }
      replacements.add(replacement);
    }
    replacements.sort(
        Comparator.comparing(StoreInfo::getCategory)
            .thenComparing(StoreInfo::getTitle));
    if (format(replacements).length() > MAX_INFORMATION_LENGTH)
      throw new BusinessException(ErrorCode.STORE_INFO_LIMIT_EXCEEDED);

    List<StoreInfo> removedItems =
        removedStoreInfoIds.stream().map(existingById::get).toList();
    infos.deleteAllInBatch(removedItems);
    List<StoreInfo> saved = infos.saveAll(replacements);
    infos.flush();
    return saved.stream().map(Response::from).toList();
  }

  @Transactional(readOnly = true)
  public Answer ask(long groupId, long requesterId, String question) {
    groups.requireGroup(groupId);
    groups.requireMember(groupId, requesterId);
    if (question == null || question.isBlank() || question.strip().length() > 200)
      throw new BusinessException(ErrorCode.INVALID_STORE_INFO_INPUT);
    List<StoreInfo> items = infos.findAllByGroupIdOrderByCategoryAscIdAsc(groupId);
    if (items.isEmpty()) return new Answer(EMPTY_ANSWER);
    return new Answer(aiClient.answer(question.strip(), format(items)));
  }

  private void ensureLimit(long groupId, StoreInfo candidate, StoreInfo replacing) {
    List<StoreInfo> all = new ArrayList<>(infos.findAllByGroupIdOrderByCategoryAscIdAsc(groupId));
    all.removeIf(info -> info == replacing);
    all.add(candidate);
    all.sort(Comparator.comparing(StoreInfo::getCategory).thenComparing(StoreInfo::getId, Comparator.nullsLast(Long::compareTo)));
    if (format(all).length() > MAX_INFORMATION_LENGTH)
      throw new BusinessException(ErrorCode.STORE_INFO_LIMIT_EXCEEDED);
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
      case LOCATION -> "매장 위치"; case PROMOTION -> "프로모션"; case DELIVERY -> "택배·입고";
      case EQUIPMENT -> "장비"; case RULE -> "운영 규칙"; case ETC -> "기타";
    };
  }

  private static Member manager(GroupService groups, long groupId, long managerId) {
    groups.requireManager(groupId, managerId);
    return groups.requireMember(groupId, managerId).getMember();
  }

  private static void validateLength(String title, String content) {
    if (title == null || title.isBlank() || title.strip().length() > 60 || content == null || content.isBlank() || content.strip().length() > 1000)
      throw new BusinessException(ErrorCode.INVALID_STORE_INFO_INPUT);
  }

  private static StoreInfoCategory parseCategory(String value) {
    try { return StoreInfoCategory.valueOf(value == null ? "" : value); }
    catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_STORE_INFO_INPUT); }
  }

  public record Response(long storeInfoId, StoreInfoCategory category, String title, String content, OffsetDateTime updatedAt) {
    static Response from(StoreInfo info) {
      return new Response(info.getId(), info.getCategory(), info.getTitle(), info.getContent(), info.getUpdatedAt().atOffset(ZoneOffset.ofHours(9)));
    }
  }
  public record ReplaceCommand(
      Long storeInfoId, String category, String title, String content) {}
  public record Answer(String answer) {}
}
