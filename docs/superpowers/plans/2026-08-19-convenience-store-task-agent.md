# Convenience Store Task Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a demo-ready Spring Boot backend that turns a manager's natural-language instructions into scheduled store tasks and verifies worker photo submissions through the specified AI API.

**Architecture:** Feature packages own their controllers, services, entities, repositories, and DTOs. AI and object storage are ports with HTTP/S3 production adapters and deterministic local adapters, while templates, schedules, assignments, and attempts remain persistence-backed domain records.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring MVC, Spring Data JPA, Bean Validation, MySQL, H2, AWS SDK v2 S3, JUnit 5, MockMvc, WireMock.

**Spec:** `docs/superpowers/specs/2026-08-19-convenience-store-task-agent-design.md`

## Global Constraints

- Preserve the existing `ApiResponse` envelope and centralized exception handling.
- Roles are exactly `MANAGER` and `WORKER`; JWT, sessions, and Spring Security are excluded.
- AI calls use the PDF contract at `/v1/tasks/generate` and `/v1/attempts/check`, a 60-second read timeout, and no personal data.
- Photos are JPEG, PNG, or WebP, from 1 byte through 10 MB, with server-computed lowercase SHA-256.
- Production storage is S3; local and test profiles work without AWS credentials.
- Template generation and assignment creation must be idempotent where the spec defines unique keys.
- Every behavioral change starts with a failing test and ends with the narrow test plus the full suite.

---

### Task 1: Runtime Configuration and Shared Errors

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yaml`
- Create: `src/test/resources/application-test.yaml`
- Modify: `src/main/java/com/ktb/hackathon/team11/global/exception/ErrorCode.java`
- Create: `src/main/java/com/ktb/hackathon/team11/global/config/TimeConfig.java`
- Test: `src/test/java/com/ktb/hackathon/team11/global/config/ApplicationConfigurationTest.java`

**Interfaces:**
- Produces: a Seoul `Clock` bean; AWS SDK, WebClient/RestClient, and WireMock dependencies; all domain error codes from the spec.
- Consumes: existing Spring Boot and `ApiResponse` infrastructure.

- [ ] **Step 1: Write the failing configuration test**

```java
@SpringBootTest
@ActiveProfiles("test")
class ApplicationConfigurationTest {
    @Autowired Clock clock;

    @Test void usesSeoulClock() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
```

- [ ] **Step 2: Run the test and confirm the missing Clock failure**

Run: `./gradlew test --tests '*ApplicationConfigurationTest'`
Expected: FAIL because no `Clock` bean exists.

- [ ] **Step 3: Add dependencies, profiles, typed external settings, Clock, and error codes**

```java
@Configuration
class TimeConfig {
    @Bean Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
}
```

Add AWS SDK S3/presigner and WireMock dependencies. Make the test profile use H2 with `ddl-auto=create-drop`, `ai.stub-enabled=true`, and `storage.local-enabled=true`. Add the exact error codes listed in spec section 13.

- [ ] **Step 4: Run focused and full tests**

Run: `./gradlew test --tests '*ApplicationConfigurationTest' && ./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add build.gradle src/main/resources src/test/resources src/main/java/com/ktb/hackathon/team11/global src/test/java/com/ktb/hackathon/team11/global
git commit -m "chore: configure task agent runtime"
```

### Task 2: Members and Group Membership

**Files:**
- Create: `src/main/java/com/ktb/hackathon/team11/member/**`
- Create: `src/main/java/com/ktb/hackathon/team11/group/**`
- Test: `src/test/java/com/ktb/hackathon/team11/member/MemberApiTest.java`
- Test: `src/test/java/com/ktb/hackathon/team11/group/GroupApiTest.java`

**Interfaces:**
- Produces: `MemberRole`, `Member`, `WorkGroup`, `GroupMember`; `MemberService.requireMember(long)`; `GroupAccessService.requireMember(long,long)`, `requireManager(long,long)`, and `requireWorker(long,long)`.
- Produces endpoints: member registration/login, group creation/join/list/member-list.
- Consumes: Task 1 errors and common response envelope.

- [ ] **Step 1: Write failing API tests for registration, login, group creation, and join**

```java
mockMvc.perform(post("/api/v1/members")
        .contentType(APPLICATION_JSON)
        .content("""{"nickname":"점장","role":"MANAGER"}"""))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.data.role").value("MANAGER"));
```

Include duplicate nickname 409, six-digit invite code, successful worker join, duplicate membership 409, and manager-only member listing.

- [ ] **Step 2: Run tests and confirm 404/missing-type failures**

Run: `./gradlew test --tests '*MemberApiTest' --tests '*GroupApiTest'`
Expected: FAIL because endpoints and entities do not exist.

- [ ] **Step 3: Implement member entities, repositories, DTO validation, service, and controller**

```java
public enum MemberRole { MANAGER, WORKER }

public record CreateMemberRequest(
    @NotBlank @Size(max = 30) String nickname,
    @NotNull MemberRole role
) {}
```

Normalize nickname with `strip()`, enforce repository uniqueness, and return 201 for registration.

- [ ] **Step 4: Implement groups and six-digit invitation membership**

```java
public interface InviteCodeGenerator { String nextCode(); }

@Transactional
public GroupResponse create(long managerId, String name) {
    Member manager = memberService.requireRole(managerId, MANAGER);
    // retry generated codes on repository collision, then save group and owner membership
}
```

Use a DB unique constraint for invite code and `(group_id, member_id)`.

- [ ] **Step 5: Run focused and full tests, then commit**

Run: `./gradlew test --tests '*MemberApiTest' --tests '*GroupApiTest' && ./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/ktb/hackathon/team11/member src/main/java/com/ktb/hackathon/team11/group src/test
git commit -m "feat: add members and store groups"
```

### Task 3: AI Port, Stub, and HTTP Adapter

**Files:**
- Create: `src/main/java/com/ktb/hackathon/team11/ai/**`
- Test: `src/test/java/com/ktb/hackathon/team11/ai/HttpAiTaskClientTest.java`
- Test: `src/test/java/com/ktb/hackathon/team11/ai/StubAiTaskClientTest.java`

**Interfaces:**
- Produces: `AiTaskClient.generateTasks(String)`, `AiTaskClient.checkPhoto(PhotoCheckCommand)`, `GeneratedTask`, `PhotoCheckCommand`, `PhotoCheckResult`, `PhotoCheckStatus`.
- Consumes: AI base URL/token/timeouts from Task 1.

- [ ] **Step 1: Write failing contract tests with WireMock**

```java
stubFor(post(urlEqualTo("/v1/tasks/generate"))
    .withHeader("Authorization", equalTo("Bearer test-token"))
    .willReturn(okJson("""{"tasks":[{"title":"POS 확인","instruction":"촬영","completionType":"PHOTO","rule":"켜짐"}]}""")));
```

Cover generate mapping, PASS, RETAKE, 400/401 mapping, 422 new-URL retry through a supplied URL factory, 503 one retry, and malformed response rejection.

- [ ] **Step 2: Run tests and confirm missing client failures**

Run: `./gradlew test --tests '*AiTaskClientTest'`
Expected: FAIL because the AI port is absent.

- [ ] **Step 3: Implement validated AI records and HTTP client**

```java
public interface AiTaskClient {
    List<GeneratedTask> generateTasks(String message);
    PhotoCheckResult checkPhoto(PhotoCheckCommand command);
}

public record PhotoCheckResult(PhotoCheckStatus status, String reason, String fix) {}
```

Use Spring `RestClient`, bearer authentication, configured timeouts, response validation, and sanitized exceptions. Retry only the statuses defined by the spec.

- [ ] **Step 4: Implement deterministic stub adapter**

The stub returns a PHOTO POS item and a CHECK cleaning item. Photo check returns RETAKE when the task rule or filename contains `retake`; otherwise PASS. Activate it only with `ai.stub-enabled=true`.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew test --tests '*AiTaskClientTest' && ./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/ktb/hackathon/team11/ai src/test/java/com/ktb/hackathon/team11/ai
git commit -m "feat: integrate task generation AI API"
```

### Task 4: Task Drafts, Templates, and Validation

**Files:**
- Create: `src/main/java/com/ktb/hackathon/team11/task/**`
- Test: `src/test/java/com/ktb/hackathon/team11/task/TaskTemplateApiTest.java`
- Test: `src/test/java/com/ktb/hackathon/team11/task/TaskItemTemplateTest.java`

**Interfaces:**
- Produces: `CompletionType`, `TaskTemplate`, `TaskItemTemplate`, repositories, draft endpoint, template create/read/list/update/deactivate endpoints.
- Produces: `TaskTemplateService.requireTemplate(long)` for scheduling.
- Consumes: `AiTaskClient`, `GroupAccessService`.

- [ ] **Step 1: Write failing draft and template tests**

```java
given(aiTaskClient.generateTasks(anyString())).willReturn(List.of(
    new GeneratedTask("POS 확인", "화면 촬영", PHOTO, "화면이 켜져 있어야 한다")
));
```

Assert AI draft generation, PHOTO requires rule, CHECK rejects rule, sequence uniqueness, manager access, list/detail, patch, and soft delete.

- [ ] **Step 2: Run tests and confirm failures**

Run: `./gradlew test --tests '*TaskTemplate*Test'`
Expected: FAIL because task types are missing.

- [ ] **Step 3: Implement draft service and DTO constraints**

```java
public record CreateTaskDraftRequest(
    @NotNull Long managerId,
    @NotBlank @Size(max = 2000) String message
) {}
```

Return a UUID tracking value and validated generated items without persisting the draft.

- [ ] **Step 4: Implement template aggregate and APIs**

Create the template and ordered item records in one transaction. Domain factory methods enforce PHOTO/CHECK invariants, and deactivate preserves all history.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew test --tests '*TaskTemplate*Test' && ./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/ktb/hackathon/team11/task src/test/java/com/ktb/hackathon/team11/task
git commit -m "feat: add AI-backed task templates"
```

### Task 5: Recurring Schedules and Assignment Generation

**Files:**
- Create: `src/main/java/com/ktb/hackathon/team11/schedule/**`
- Create: `src/main/java/com/ktb/hackathon/team11/assignment/**`
- Test: `src/test/java/com/ktb/hackathon/team11/schedule/TaskScheduleTest.java`
- Test: `src/test/java/com/ktb/hackathon/team11/schedule/AssignmentGenerationServiceTest.java`
- Test: `src/test/java/com/ktb/hackathon/team11/assignment/AssignmentApiTest.java`

**Interfaces:**
- Produces: `RecurrenceType`, `AssignmentStatus`, `TaskSchedule`, `TaskAssignment`, `AssignmentGenerationService.generate(long, LocalDate)`, nightly scheduler, manual generation and query endpoints.
- Consumes: templates/items from Task 4 and group/member access from Task 2.

- [ ] **Step 1: Write failing recurrence and overnight-window tests**

```java
assertThat(weekly.occursOn(LocalDate.of(2026, 8, 21))).isTrue();
assertThat(overnight.windowFor(date).dueAt()).isEqualTo(date.plusDays(1).atTime(6, 20));
```

Cover ONCE, DAILY, WEEKLY, start/end bounds, invalid weekdays, assignee membership, early/late allowances, and end-before-start overnight calculation.

- [ ] **Step 2: Write failing idempotent generation and query tests**

Generate the same date twice and assert one assignment per schedule/item/date. Assert a worker sees directly assigned and unassigned group work but not another worker's direct assignments.

- [ ] **Step 3: Run tests and confirm failures**

Run: `./gradlew test --tests '*TaskScheduleTest' --tests '*AssignmentGenerationServiceTest' --tests '*AssignmentApiTest'`
Expected: FAIL because schedule and assignment types are absent.

- [ ] **Step 4: Implement schedule value logic, entities, generator, and scheduler**

```java
@Scheduled(cron = "${task.assignment-generation-cron:0 0 0 * * *}", zone = "Asia/Seoul")
void generateTomorrow() {
    generationService.generateAll(LocalDate.now(clock).plusDays(1));
}
```

Back idempotency with `(schedule_id, task_item_template_id, scheduled_date)` unique and recover cleanly from concurrent duplicate inserts.

- [ ] **Step 5: Implement manual generation and assignment queries**

Expose the endpoints from spec sections 12.5 and map assignment status and time windows without exposing entities.

- [ ] **Step 6: Run tests and commit**

Run: `./gradlew test --tests '*schedule*' --tests '*assignment*' && ./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/ktb/hackathon/team11/schedule src/main/java/com/ktb/hackathon/team11/assignment src/test
git commit -m "feat: generate recurring task assignments"
```

### Task 6: File Storage and Photo Inspection

**Files:**
- Create: `src/main/java/com/ktb/hackathon/team11/storage/**`
- Test: `src/test/java/com/ktb/hackathon/team11/storage/PhotoInspectorTest.java`
- Test: `src/test/java/com/ktb/hackathon/team11/storage/S3FileStorageTest.java`

**Interfaces:**
- Produces: `FileStorage.store(StorageCommand)`, `createReadUrl(String,Duration)`, `delete(String)`; `PhotoInspector.inspect(MultipartFile)` returning MIME, extension, size, sha256, and bytes.
- Consumes: AWS properties from Task 1.

- [ ] **Step 1: Write failing file inspection tests**

Use minimal valid JPEG/PNG/WebP fixtures and assert signature-derived MIME, SHA-256, empty rejection, unsupported signature rejection, and >10MB rejection.

- [ ] **Step 2: Run tests and confirm failures**

Run: `./gradlew test --tests '*PhotoInspectorTest' --tests '*S3FileStorageTest'`
Expected: FAIL because storage types are absent.

- [ ] **Step 3: Implement bounded photo reading and signature inspection**

```java
public record InspectedPhoto(byte[] bytes, String mimeType, String extension,
                             long sizeBytes, String sha256) {}
```

Read at most 10MB + 1 byte, derive MIME from magic bytes, and compute SHA-256 from the exact stored bytes.

- [ ] **Step 4: Implement local and S3 storage adapters**

Local storage keeps test bytes in a concurrent map and returns an HTTPS-shaped demo URL. S3 uses `S3Client.putObject`, `S3Presigner`, private objects, and deletes a just-uploaded object on transaction failure when requested by the caller.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew test --tests '*storage*' && ./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/ktb/hackathon/team11/storage src/test/java/com/ktb/hackathon/team11/storage
git commit -m "feat: add S3 photo storage"
```

### Task 7: Task Completion and AI Photo Attempts

**Files:**
- Create: `src/main/java/com/ktb/hackathon/team11/attempt/**`
- Modify: `src/main/java/com/ktb/hackathon/team11/assignment/**`
- Test: `src/test/java/com/ktb/hackathon/team11/attempt/TaskAttemptServiceTest.java`
- Test: `src/test/java/com/ktb/hackathon/team11/attempt/TaskAttemptApiTest.java`

**Interfaces:**
- Produces: `AttemptStatus`, `TaskAttempt`, `TaskPhoto`, check-completion endpoint, photo-attempt endpoint, history endpoint, delayed retry endpoint.
- Consumes: `PhotoInspector`, `FileStorage`, `AiTaskClient`, `Clock`, assignments, group access.

- [ ] **Step 1: Write failing CHECK completion and time-window tests**

Assert CHECK completes only inside its window, PHOTO cannot use the check endpoint, completed/expired work rejects changes, and optimistic state changes do not regress.

- [ ] **Step 2: Write failing PHOTO attempt tests**

```java
given(aiTaskClient.checkPhoto(any())).willReturn(
    new PhotoCheckResult(PhotoCheckStatus.RETAKE, "화면이 보이지 않습니다.", "다시 촬영해 주세요."));
```

Cover PASS, RETAKE, duplicate `(group, sha256)`, delayed outcome after AI unavailable, attempt numbering, history, and manager retry.

- [ ] **Step 3: Run tests and confirm failures**

Run: `./gradlew test --tests '*TaskAttempt*Test'`
Expected: FAIL because attempt workflow is absent.

- [ ] **Step 4: Implement assignment state transitions and CHECK service**

```java
public void completeCheck(Member worker, Clock clock) {
    requireAvailableAt(LocalDateTime.now(clock));
    requireCompletionType(CHECK);
    status = COMPLETED;
    completedAt = LocalDateTime.now(clock);
}
```

- [ ] **Step 5: Implement PHOTO submission transaction and retry**

Inspect and hash before upload, reserve unique photo metadata, upload with a UUID key, create attempt, call AI outside no state-losing path, then persist PASS/RETAKE/DELAYED. A delayed retry reuses the stored object with a fresh URL and never creates a second photo row.

- [ ] **Step 6: Implement attempt APIs and safe error responses**

Return assignment status, attempt id, reason, and optional fix. Do not expose provider error bodies, bucket names, credentials, or raw object keys.

- [ ] **Step 7: Run tests and commit**

Run: `./gradlew test --tests '*TaskAttempt*Test' && ./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/ktb/hackathon/team11/attempt src/main/java/com/ktb/hackathon/team11/assignment src/test
git commit -m "feat: verify worker task submissions"
```

### Task 8: End-to-End Demo Documentation and Verification

**Files:**
- Create: `src/test/java/com/ktb/hackathon/team11/demo/DemoFlowIntegrationTest.java`
- Create: `docs/api/task-agent-api.md`
- Create: `docs/demo/task-agent-demo.md`
- Modify: `HELP.md`

**Interfaces:**
- Produces: executable full-flow test and copy-paste HTTP demo guide.
- Consumes: all earlier endpoints and test-profile adapters.

- [ ] **Step 1: Write the failing end-to-end flow test**

Use MockMvc to register manager/worker, create/join group, generate AI draft, register template/schedule, generate today's assignments, complete CHECK, submit RETAKE then PASS photos, and query manager status.

- [ ] **Step 2: Run the end-to-end test and fix only integration gaps**

Run: `./gradlew test --tests '*DemoFlowIntegrationTest'`
Expected: PASS after resolving serialization, transaction, and endpoint wiring gaps without weakening assertions.

- [ ] **Step 3: Write API and demo documents with exact commands**

Document environment variables, `./gradlew bootRun --args='--spring.profiles.active=local'`, endpoint tables, request/response examples, curl commands, expected status changes, and production AI/S3 configuration.

- [ ] **Step 4: Run final verification**

Run: `./gradlew clean test jacocoTestReport`
Expected: BUILD SUCCESSFUL, all tests pass, and `build/reports/jacoco/test/html/index.html` exists.

- [ ] **Step 5: Inspect the final diff and commit**

Run: `git diff --check && git status --short`
Expected: no whitespace errors and only intended files.

```bash
git add src/test/java/com/ktb/hackathon/team11/demo docs HELP.md
git commit -m "docs: add task agent API demo guide"
```
