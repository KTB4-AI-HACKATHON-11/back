package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.*;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.schedule.*;
import com.ktb.hackathon.team11.storage.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TaskRegistrationService {
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

  private final TaskTemplateRepository templates;
  private final TaskItemTemplateRepository items;
  private final TaskScheduleRepository schedules;
  private final TaskAssignmentRepository assignments;
  private final GroupService groups;
  private final GroupMemberRepository memberships;
  private final MemberService members;
  private final PhotoInspector photoInspector;
  private final FileStorage storage;
  private final Clock clock;

  @Transactional
  public TaskCreatedResponse create(
      long groupId,
      long managerId,
      String title,
      String message,
      long workerId,
      OffsetDateTime dueAt,
      List<ChecklistCommand> commands,
      List<MultipartFile> referencePhotos) {
    GroupMember manager = groups.requireManager(groupId, managerId);
    Member worker = requireGroupWorker(groupId, workerId);
    LocalDateTime availableFrom = LocalDateTime.now(clock);
    LocalDateTime due = dueAt.atZoneSameInstant(SERVICE_ZONE).toLocalDateTime();
    if (!due.isAfter(availableFrom)) throw new BusinessException(ErrorCode.INVALID_SCHEDULE);

    List<PhotoInspector.InspectedPhoto> inspectedPhotos =
        validateAndInspect(commands, referencePhotos);
    TaskTemplate template =
        templates.save(
            new TaskTemplate(manager.getGroup(), manager.getMember(), title.strip(), message.strip()));

    List<TaskItemTemplate> savedItems = new ArrayList<>(commands.size());
    List<String> storedKeys = new ArrayList<>();
    registerRollbackCleanup(storedKeys);

    for (ChecklistCommand command : commands) {
      TaskItemTemplate item =
          items.save(
              new TaskItemTemplate(
                  template,
                  command.sequence(),
                  command.title().strip(),
                  command.instruction().strip(),
                  command.completionType(),
                  command.rule()));
      if (command.completionType() == CompletionType.PHOTO
          && command.referencePhotoIndex() != null) {
        PhotoInspector.InspectedPhoto photo =
            inspectedPhotos.get(command.referencePhotoIndex());
        String key =
            "groups/"
                + groupId
                + "/tasks/"
                + template.getId()
                + "/references/"
                + item.getId()
                + "-"
                + UUID.randomUUID()
                + "."
                + photo.extension();
        storage.store(key, photo.bytes(), photo.mimeType());
        storedKeys.add(key);
        item.setReferenceImage(key, photo.mimeType(), photo.sizeBytes(), photo.sha256());
      }
      savedItems.add(item);
    }

    TaskSchedule schedule =
        schedules.save(new TaskSchedule(template, worker, availableFrom, due));
    List<TaskAssignment> savedAssignments =
        savedItems.stream()
            .map(
                item ->
                    assignments.save(
                        new TaskAssignment(
                            schedule, item, availableFrom.toLocalDate(), availableFrom, due)))
            .toList();

    List<ChecklistCreatedResponse> checklistResponses = new ArrayList<>(savedItems.size());
    for (int index = 0; index < savedItems.size(); index++) {
      TaskItemTemplate item = savedItems.get(index);
      TaskAssignment assignment = savedAssignments.get(index);
      checklistResponses.add(
          new ChecklistCreatedResponse(
              item.getId(),
              assignment.getId(),
              item.getSequence(),
              item.getTitle(),
              item.getInstruction(),
              item.getCompletionType(),
              item.getVerificationRule(),
              item.hasReferenceImage(),
              assignment.getStatus()));
    }

    return new TaskCreatedResponse(
        template.getId(),
        groupId,
        template.getTitle(),
        new WorkerResponse(worker.getId(), worker.getNickname()),
        dueAt,
        AssignmentStatus.PENDING,
        List.copyOf(checklistResponses));
  }

  private Member requireGroupWorker(long groupId, long workerId) {
    Member worker = members.requireMember(workerId);
    if (worker.getRole() != MemberRole.WORKER
        || memberships.findByGroupIdAndMemberId(groupId, workerId).isEmpty())
      throw new BusinessException(ErrorCode.WORKER_NOT_IN_GROUP);
    return worker;
  }

  private List<PhotoInspector.InspectedPhoto> validateAndInspect(
      List<ChecklistCommand> commands, List<MultipartFile> referencePhotos) {
    if (commands == null || commands.isEmpty() || commands.size() > 20)
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

    boolean[] usedPhotoIndexes = new boolean[referencePhotos.size()];
    for (int index = 0; index < commands.size(); index++) {
      ChecklistCommand command = commands.get(index);
      if (command.sequence() != index + 1)
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
      validateCompletion(command);

      if (command.completionType() == CompletionType.PHOTO) {
        Integer photoIndex = command.referencePhotoIndex();
        if (photoIndex != null
            && (photoIndex < 0
                || photoIndex >= referencePhotos.size()
                || usedPhotoIndexes[photoIndex]))
          throw new BusinessException(ErrorCode.INVALID_REFERENCE_PHOTO_INDEX);
        if (photoIndex != null) usedPhotoIndexes[photoIndex] = true;
      } else if (command.referencePhotoIndex() != null) {
        throw new BusinessException(ErrorCode.INVALID_REFERENCE_PHOTO_INDEX);
      }
    }
    for (boolean used : usedPhotoIndexes)
      if (!used) throw new BusinessException(ErrorCode.INVALID_REFERENCE_PHOTO_INDEX);
    return referencePhotos.stream().map(photoInspector::inspect).toList();
  }

  private void validateCompletion(ChecklistCommand command) {
    if (command.completionType() == null)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    if (command.completionType() == CompletionType.PHOTO
        && (command.rule() == null || command.rule().isBlank()))
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    if (command.completionType() == CompletionType.CHECK && command.rule() != null)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
  }

  private void registerRollbackCleanup(List<String> storedKeys) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) storedKeys.forEach(storage::delete);
          }
        });
  }

  public record ChecklistCommand(
      int sequence,
      String title,
      String instruction,
      CompletionType completionType,
      String rule,
      Integer referencePhotoIndex) {}

  public record WorkerResponse(Long workerId, String nickname) {}

  public record ChecklistCreatedResponse(
      Long checklistId,
      Long assignmentId,
      int sequence,
      String title,
      String instruction,
      CompletionType completionType,
      String rule,
      boolean referencePhotoAttached,
      AssignmentStatus status) {}

  public record TaskCreatedResponse(
      Long taskId,
      Long groupId,
      String title,
      WorkerResponse worker,
      OffsetDateTime dueAt,
      AssignmentStatus status,
      List<ChecklistCreatedResponse> checklists) {}
}
