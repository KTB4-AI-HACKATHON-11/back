package com.ktb.hackathon.team11.notification;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.GroupService;
import com.ktb.hackathon.team11.member.Member;
import com.ktb.hackathon.team11.task.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskNotificationPreferenceService {
  private final TaskTemplateRepository templates;
  private final GroupService groups;
  private final NotificationOutboxRepository outboxes;

  @Transactional
  public boolean update(long taskId, Member currentMember, boolean enabled) {
    TaskTemplate template =
        templates
            .findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    groups.requireManager(template.getGroup().getId(), currentMember.getId());
    if (!template.getCreator().getId().equals(currentMember.getId()))
      throw new BusinessException(ErrorCode.GROUP_ACCESS_DENIED);
    template.updateCompletionNotification(enabled);
    if (!enabled) {
      outboxes
          .findAllByTaskTemplateIdAndStatusAndType(
              taskId, NotificationOutboxStatus.PENDING, NotificationType.TASK_COMPLETED)
          .forEach(item -> item.cancel("태스크 완료 알림이 해제되었습니다."));
    }
    return template.isNotifyOnCompletion();
  }
}
