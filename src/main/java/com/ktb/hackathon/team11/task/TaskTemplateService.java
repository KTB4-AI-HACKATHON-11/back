package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.ai.*;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.*;
import com.ktb.hackathon.team11.storage.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskTemplateService {
  private final TaskTemplateRepository templates;
  private final TaskItemTemplateRepository items;
  private final GroupService groups;
  private final AiTaskClient ai;
  private final PhotoInspector photoInspector;
  private final FileStorage storage;

  public DraftResponse draft(long groupId, long managerId, String message) {
    groups.requireManager(groupId, managerId);
    return new DraftResponse(UUID.randomUUID(), ai.generateTasks(message));
  }

  @Transactional
  public TaskTemplate create(
      long groupId, long managerId, String title, String source, List<ItemCommand> commands) {
    GroupMember gm = groups.requireManager(groupId, managerId);
    TaskTemplate t = templates.save(new TaskTemplate(gm.getGroup(), gm.getMember(), title, source));
    int seq = 1;
    for (ItemCommand c : commands)
      items.save(
          new TaskItemTemplate(
              t, seq++, c.title(), c.instruction(), c.completionType(), c.verificationRule()));
    return t;
  }

  public TaskTemplate require(long id) {
    return templates
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
  }

  public List<TaskItemTemplate> items(long id) {
    return items.findAllByTaskTemplateIdOrderBySequence(id);
  }

  public List<TaskTemplate> list(long groupId, long memberId) {
    groups.requireMember(groupId, memberId);
    return templates.findAllByGroupIdAndActiveTrue(groupId);
  }

  @Transactional
  public void deactivate(long id, long managerId) {
    TaskTemplate t = require(id);
    groups.requireManager(t.getGroup().getId(), managerId);
    t.deactivate();
  }

  @Transactional
  public TaskTemplate update(long id, long managerId, String title, Boolean active) {
    TaskTemplate t = require(id);
    groups.requireManager(t.getGroup().getId(), managerId);
    t.update(title, active);
    return t;
  }

  @Transactional
  public String addReferenceImage(long itemId, long managerId, MultipartFile file) {
    TaskItemTemplate item =
        items
            .findById(itemId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
    TaskTemplate t = item.getTaskTemplate();
    groups.requireManager(t.getGroup().getId(), managerId);
    PhotoInspector.InspectedPhoto p = photoInspector.inspect(file);
    String key =
        "groups/"
            + t.getGroup().getId()
            + "/templates/"
            + t.getId()
            + "/references/"
            + UUID.randomUUID()
            + "."
            + p.extension();
    storage.store(key, p.bytes(), p.mimeType());
    item.setReferenceImageKey(key);
    return key;
  }

  public record DraftResponse(UUID draftId, List<GeneratedTask> items) {}

  public record ItemCommand(
      String title, String instruction, CompletionType completionType, String verificationRule) {}
}
