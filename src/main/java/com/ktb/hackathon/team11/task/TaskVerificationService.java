package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.*;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.schedule.TaskSchedule;
import com.ktb.hackathon.team11.schedule.TaskScheduleRepository;
import com.ktb.hackathon.team11.storage.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TaskVerificationService {
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

  private final TaskTemplateRepository templates;
  private final TaskAssignmentRepository assignments;
  private final TaskScheduleRepository schedules;
  private final GroupService groups;
  private final PhotoInspector photoInspector;
  private final FileStorage storage;
  private final Clock clock;

  @Value("${storage.presigned-url-minutes:5}") private long urlMinutes;

  @Transactional
  public UpdatedSettings update(
      long taskId,
      long managerId,
      long workerId,
      OffsetDateTime dueAt,
      List<ItemCommand> commands,
      List<MultipartFile> referencePhotos) {
    TaskTemplate template =
        templates.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    GroupMember manager = groups.requireMember(template.getGroup().getId(), managerId);
    if (manager.getGroupRole() != MemberRole.MANAGER)
      throw new BusinessException(ErrorCode.VERIFICATION_SETTINGS_UPDATE_FORBIDDEN);
    Member worker = groups.requireWorker(template.getGroup().getId(), workerId).getMember();

    LocalDateTime newDueAt = dueAt.atZoneSameInstant(SERVICE_ZONE).toLocalDateTime();
    if (!newDueAt.isAfter(LocalDateTime.now(clock)))
      throw new BusinessException(ErrorCode.INVALID_DUE_AT);

    List<TaskAssignment> taskAssignments =
        assignments.findAllByScheduleTaskTemplateId(taskId).stream()
            .sorted(Comparator.comparing(a -> a.getTaskItemTemplate().getSequence()))
            .toList();
    if (taskAssignments.isEmpty()) throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    if (taskAssignments.stream().anyMatch(a -> a.getStatus() != AssignmentStatus.PENDING))
      throw new BusinessException(ErrorCode.TASK_ALREADY_STARTED);
    if (commands == null || commands.size() != taskAssignments.size())
      throw new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND);

    Map<Long, ItemCommand> commandsById = new HashMap<>();
    for (ItemCommand command : commands) {
      if (command == null || commandsById.put(command.checklistId(), command) != null)
        throw new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND);
    }
    for (TaskAssignment assignment : taskAssignments)
      if (!commandsById.containsKey(assignment.getId()))
        throw new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND);

    List<MultipartFile> photos = referencePhotos == null ? List.of() : referencePhotos;
    List<PhotoInspector.InspectedPhoto> inspectedPhotos = inspectPhotos(commands, photos);
    List<String> oldKeys = new ArrayList<>();
    for (TaskAssignment assignment : taskAssignments) {
      ItemCommand command = commandsById.get(assignment.getId());
      TaskItemTemplate item = assignment.getTaskItemTemplate();
      validateCommand(command, photos);
      item.setEnabled(command.enabled());
      item.updateVerification(command.completionType(), command.rule());

      if (item.hasReferenceImage()) oldKeys.add(item.getReferenceImageKey());
      if (command.referencePhotoIndex() == null) {
        if (item.hasReferenceImage()) item.clearReferenceImage();
      } else {
        PhotoInspector.InspectedPhoto photo = inspectedPhotos.get(command.referencePhotoIndex());
        String key =
            "groups/"
                + template.getGroup().getId()
                + "/tasks/"
                + taskId
                + "/references/"
                + item.getId()
                + "-"
                + UUID.randomUUID()
                + "."
                + photo.extension();
        storage.store(key, photo.bytes(), photo.mimeType());
        item.setReferenceImage(key, photo.mimeType(), photo.sizeBytes(), photo.sha256());
      }
      assignment.updateSchedule(worker, newDueAt);
    }
    TaskSchedule schedule =
        schedules.findFirstByTaskTemplateIdOrderByIdDesc(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    schedule.updateAssignee(worker, newDueAt);
    deleteAfterCommit(oldKeys);

    return new UpdatedSettings(
        taskId,
        workerId,
        dueAt,
        taskAssignments.stream().sorted(Comparator.comparing(a -> a.getTaskItemTemplate().getSequence()))
            .map(a -> responseItem(a, commandsById.get(a.getId())))
            .toList());
  }

  private void validateCommand(ItemCommand command, List<MultipartFile> photos) {
    if (command.completionType() == null)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    if (command.completionType() == CompletionType.PHOTO) {
      if (command.rule() == null || command.rule().isBlank())
        throw new BusinessException(ErrorCode.VERIFICATION_RULE_REQUIRED);
    } else {
      if (command.rule() != null || command.referencePhotoIndex() != null)
        throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    }
    if (command.referencePhotoIndex() != null
        && (command.referencePhotoIndex() < 0 || command.referencePhotoIndex() >= photos.size()))
      throw new BusinessException(ErrorCode.INVALID_REFERENCE_PHOTO_INDEX);
  }

  private List<PhotoInspector.InspectedPhoto> inspectPhotos(
      List<ItemCommand> commands, List<MultipartFile> photos) {
    List<PhotoInspector.InspectedPhoto> inspected = new ArrayList<>(photos.size());
    for (MultipartFile photo : photos) inspected.add(photoInspector.inspect(photo));
    Set<Integer> used = new HashSet<>();
    for (ItemCommand command : commands) {
      if (command.referencePhotoIndex() != null && !used.add(command.referencePhotoIndex()))
        throw new BusinessException(ErrorCode.INVALID_REFERENCE_PHOTO_INDEX);
    }
    return inspected;
  }

  private UpdatedItem responseItem(TaskAssignment assignment, ItemCommand command) {
    TaskItemTemplate item = assignment.getTaskItemTemplate();
    return new UpdatedItem(
        assignment.getId(),
        command.enabled(),
        command.completionType(),
        command.rule(),
        item.hasReferenceImage()
            ? storage.createReadUrl(item.getReferenceImageKey(), Duration.ofMinutes(urlMinutes))
            : null);
  }

  private void deleteAfterCommit(List<String> keys) {
    if (keys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            keys.forEach(storage::delete);
          }
        });
  }

  public record ItemCommand(
      long checklistId,
      boolean enabled,
      CompletionType completionType,
      String rule,
      Integer referencePhotoIndex) {}

  public record UpdatedSettings(
      long taskId, long workerId, OffsetDateTime dueAt, List<UpdatedItem> items) {}

  public record UpdatedItem(
      long checklistId,
      boolean enabled,
      CompletionType completionType,
      String rule,
      String referencePhotoUrl) {}
}
