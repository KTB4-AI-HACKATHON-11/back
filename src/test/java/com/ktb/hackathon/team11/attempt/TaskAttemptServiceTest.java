package com.ktb.hackathon.team11.attempt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ktb.hackathon.team11.ai.*;
import com.ktb.hackathon.team11.assignment.*;
import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.group.*;
import com.ktb.hackathon.team11.member.*;
import com.ktb.hackathon.team11.notification.CompletionNotificationService;
import com.ktb.hackathon.team11.schedule.TaskSchedule;
import com.ktb.hackathon.team11.storage.*;
import com.ktb.hackathon.team11.task.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskAttemptServiceTest {
  @Mock private TaskAttemptRepository attempts;
  @Mock private TaskPhotoRepository photos;
  @Mock private AssignmentService assignmentService;
  @Mock private MemberService members;
  @Mock private GroupService groups;
  @Mock private PhotoInspector inspector;
  @Mock private FileStorage storage;
  @Mock private AiTaskClient ai;
  @Mock private CompletionNotificationService completionNotifications;
  @Mock private TaskAssignment assignment;
  @Mock private TaskSchedule schedule;
  @Mock private TaskTemplate template;
  @Mock private TaskItemTemplate item;
  @Mock private WorkGroup group;
  @Mock private GroupMember workerMembership;
  @Mock private Member worker;

  private TaskAttemptService service;
  private MockMultipartFile file;
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-08-20T01:00:00Z"), ZoneId.of("Asia/Seoul"));

  @BeforeEach
  void setUp() {
    service =
        new TaskAttemptService(
            attempts,
            photos,
            assignmentService,
            members,
            groups,
            inspector,
            storage,
            ai,
            completionNotifications,
            clock);
    ReflectionTestUtils.setField(service, "urlMinutes", 5L);
    file = new MockMultipartFile("photo", "proof.png", "image/png", new byte[] {1, 2});

    when(members.requireRole(2L, MemberRole.WORKER)).thenReturn(worker);
    when(worker.getId()).thenReturn(2L);
    when(assignmentService.requireForUpdate(15L)).thenReturn(assignment);
    when(assignment.getSchedule()).thenReturn(schedule);
    when(schedule.getTaskTemplate()).thenReturn(template);
    when(template.getGroup()).thenReturn(group);
    when(group.getId()).thenReturn(1L);
    when(groups.requireMember(1L, 2L)).thenReturn(workerMembership);
    when(workerMembership.getGroupRole()).thenReturn(MemberRole.WORKER);
    when(assignment.getAssignee()).thenReturn(worker);
    when(assignment.getTaskItemTemplate()).thenReturn(item);
    when(item.getCompletionType()).thenReturn(CompletionType.PHOTO);
    when(item.hasReferenceImage()).thenReturn(true);
    when(item.getTitle()).thenReturn("매장 전경 촬영");
    when(item.getInstruction()).thenReturn("매장 전체가 보이도록 촬영해 주세요.");
    when(item.getVerificationRule()).thenReturn("조명과 진열대가 보여야 한다.");
    when(item.getReferenceImageKey()).thenReturn("groups/1/references/reference.png");
    when(item.getReferenceImageMimeType()).thenReturn("image/png");
    when(item.getReferenceImageSizeBytes()).thenReturn(20L);
    when(item.getReferenceImageSha256()).thenReturn("b".repeat(64));
    when(inspector.inspect(file))
        .thenReturn(
            new PhotoInspector.InspectedPhoto(
                new byte[] {1, 2}, "image/png", "png", 2, "a".repeat(64)));
    when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(storage.store(anyString(), any(), eq("image/png")))
        .thenReturn(new StoredFile("ignored", "https://s3.example/submitted"));
    when(storage.createReadUrl(eq("groups/1/references/reference.png"), any()))
        .thenReturn("https://s3.example/reference");
  }

  @Test
  void passStoresPhotoAndCompletesAssignment() {
    when(assignment.getStatus()).thenReturn(AssignmentStatus.COMPLETED);
    when(ai.checkPhoto(any()))
        .thenReturn(new PhotoCheckResult(PhotoCheckStatus.PASS, "기준을 충족합니다.", null));

    TaskAttemptService.Result result = service.submit(15L, 2L, file);

    assertThat(result.status()).isEqualTo(AttemptStatus.PASS);
    assertThat(result.assignmentStatus()).isEqualTo(AssignmentStatus.COMPLETED);
    verify(assignment).requirePhotoSubmissionAvailable(LocalDateTime.now(clock));
    verify(assignment).pass(LocalDateTime.now(clock));
    verify(photos).save(any(TaskPhoto.class));
    ArgumentCaptor<PhotoCheckCommand> command = ArgumentCaptor.forClass(PhotoCheckCommand.class);
    verify(ai).checkPhoto(command.capture());
    assertThat(command.getValue().photo().url()).isEqualTo("https://s3.example/submitted");
    assertThat(command.getValue().referencePhoto().url()).isEqualTo("https://s3.example/reference");
  }

  @Test
  void passesWorkerPhotoToAiWithoutOptionalReferencePhoto() {
    when(item.hasReferenceImage()).thenReturn(false);
    when(assignment.getStatus()).thenReturn(AssignmentStatus.COMPLETED);
    when(ai.checkPhoto(any()))
        .thenReturn(new PhotoCheckResult(PhotoCheckStatus.PASS, "기준을 충족합니다.", null));

    TaskAttemptService.Result result = service.submit(15L, 2L, file);

    assertThat(result.status()).isEqualTo(AttemptStatus.PASS);
    ArgumentCaptor<PhotoCheckCommand> command = ArgumentCaptor.forClass(PhotoCheckCommand.class);
    verify(ai).checkPhoto(command.capture());
    assertThat(command.getValue().photo()).isNotNull();
    assertThat(command.getValue().referencePhoto()).isNull();
    verify(storage, never()).createReadUrl(eq("groups/1/references/reference.png"), any());
  }

  @Test
  void retakeKeepsAttemptAndRequestsAnotherPhoto() {
    when(assignment.getStatus()).thenReturn(AssignmentStatus.RETAKE_REQUIRED);
    when(ai.checkPhoto(any()))
        .thenReturn(
            new PhotoCheckResult(
                PhotoCheckStatus.RETAKE, "진열대가 보이지 않습니다.", "전체가 보이도록 다시 촬영해 주세요."));

    TaskAttemptService.Result result = service.submit(15L, 2L, file);

    assertThat(result.status()).isEqualTo(AttemptStatus.RETAKE);
    assertThat(result.fix()).contains("다시 촬영");
    verify(assignment).retake();
  }

  @Test
  void aiFailureKeepsSubmissionAsDelayed() {
    when(assignment.getStatus()).thenReturn(AssignmentStatus.VERIFICATION_DELAYED);
    when(ai.checkPhoto(any())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

    TaskAttemptService.Result result = service.submit(15L, 2L, file);

    assertThat(result.status()).isEqualTo(AttemptStatus.DELAYED);
    assertThat(result.reason()).isEqualTo("AI 검사 지연 중입니다.");
    verify(assignment).delayed();
    verify(storage, never()).delete(anyString());
  }

  @Test
  void refreshesUnavailableReferencePhotoUrlOnce() {
    when(assignment.getStatus()).thenReturn(AssignmentStatus.COMPLETED);
    when(storage.createReadUrl(eq("groups/1/references/reference.png"), any()))
        .thenReturn("https://s3.example/reference-1", "https://s3.example/reference-2");
    when(ai.checkPhoto(any()))
        .thenThrow(new PhotoUnavailableException("referencePhoto"))
        .thenReturn(new PhotoCheckResult(PhotoCheckStatus.PASS, "기준을 충족합니다.", null));

    TaskAttemptService.Result result = service.submit(15L, 2L, file);

    assertThat(result.status()).isEqualTo(AttemptStatus.PASS);
    verify(storage, times(2))
        .createReadUrl(eq("groups/1/references/reference.png"), any());
    ArgumentCaptor<PhotoCheckCommand> commands =
        ArgumentCaptor.forClass(PhotoCheckCommand.class);
    verify(ai, times(2)).checkPhoto(commands.capture());
    assertThat(commands.getAllValues().get(1).referencePhoto().url())
        .isEqualTo("https://s3.example/reference-2");
  }

  @Test
  void rejectsPhotoAlreadyUsedInSameGroupBeforeUpload() {
    when(photos.existsByGroupIdAndSha256(1L, "a".repeat(64))).thenReturn(true);

    assertThatThrownBy(() -> service.submit(15L, 2L, file))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.DUPLICATE_PHOTO);

    verifyNoInteractions(ai);
    verify(storage, never()).store(anyString(), any(), anyString());
  }

  @Test
  void fatalAiErrorRollsBackAndDeletesUploadedPhoto() {
    when(ai.checkPhoto(any())).thenThrow(new BusinessException(ErrorCode.AI_UNAUTHORIZED));
    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThatThrownBy(() -> service.submit(15L, 2L, file))
          .isInstanceOf(BusinessException.class)
          .extracting(exception -> ((BusinessException) exception).getErrorCode())
          .isEqualTo(ErrorCode.AI_UNAUTHORIZED);

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization ->
                  synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      verify(storage).delete(startsWith("groups/1/assignments/15/attempts/"));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
