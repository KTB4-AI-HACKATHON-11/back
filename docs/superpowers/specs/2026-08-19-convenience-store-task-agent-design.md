# 편의점 업무 관리 AI 에이전트 설계 명세

## 1. 목적

야간 편의점의 관리자와 알바생 사이에서 반복 업무의 생성, 배정, 인수인계, 수행 확인을 대신하는 백엔드 서비스를 구현한다. 관리자는 자연어로 업무를 입력하고, AI가 이를 실행 가능한 하위 업무로 나눈다. 알바생은 지정된 시간에 업무를 수행하며, 사진이 필요한 업무는 AI 판정을 통과해야 완료된다.

이 명세의 구현 대상은 Java 21, Spring Boot 4, Spring Data JPA, MySQL 기반 백엔드다. 인증과 인가는 데모 범위에서 제외한다.

## 2. 핵심 원칙

- 재사용 가능한 `업무 템플릿`과 특정 날짜에 수행하는 `업무 배정`을 분리한다.
- AI 연동은 인터페이스 뒤에 격리하여 프롬프트와 공급자를 추후 교체할 수 있게 한다.
- 사진은 백엔드가 multipart로 받아 S3에 저장하고 AI 서버에는 임시 읽기 URL과 무결성 정보를 전달한다.
- 수행 결과와 재시도 이력을 삭제하지 않는다.
- 반복 생성과 사진 제출은 같은 요청이 반복되어도 데이터가 중복되거나 상태가 역행하지 않게 한다.

## 3. 범위

### 3.1 포함 기능

- 닉네임과 역할을 사용하는 회원가입
- 닉네임 기반 데모 로그인
- `MANAGER`, `WORKER` 역할
- 그룹 생성과 고유한 6자리 숫자 초대 코드 발급
- 초대 코드로 그룹 가입
- 자연어에서 AI 태스크 초안 생성
- AI 초안 수정 후 업무 템플릿 등록
- 사진 기준 이미지 선택 등록
- `PHOTO`, `CHECK` 완료 방식
- 특정 알바생 배정과 담당자 미지정 근무 시간대 배정
- 일회, 매일, 특정 요일 반복 일정
- 자동 스케줄러와 데모용 수동 배정 생성
- 알바생별 및 그룹별 업무 조회
- 수행 가능 시간 검사
- S3 사진 업로드와 presigned URL 생성
- MIME, 크기, SHA-256 검증
- 동일 그룹 내 제출 사진 재사용 차단
- AI 사진 검증 및 재촬영 안내
- AI 장애 시 재시도와 검사 지연 상태 보존
- 수행 및 제출 이력 조회
- API 통합 테스트와 주요 도메인 단위 테스트

### 3.2 제외 기능

- JWT, 세션, Spring Security
- 푸시 알림
- 교대 근무표 편집
- 통계 대시보드
- 프롬프트 최적화
- 메시지 큐 기반 비동기 처리
- S3 객체 수명 주기와 자동 삭제 정책

## 4. 사용자와 역할

### 4.1 관리자

- 그룹을 생성한다.
- AI로 업무 초안을 생성하고 수정하여 템플릿으로 등록한다.
- 특정 알바생 또는 근무 시간대에 반복 업무를 배정한다.
- 지정 날짜의 실제 업무를 수동 생성할 수 있다.
- 그룹 업무 현황과 제출 이력을 조회한다.
- 검사 지연 제출을 다시 검사할 수 있다.

### 4.2 알바생

- 초대 코드로 그룹에 가입한다.
- 자신에게 직접 배정된 업무와 담당자 미지정 업무를 조회한다.
- `CHECK` 업무를 직접 완료한다.
- `PHOTO` 업무에 사진을 제출하고 AI 판정 결과를 확인한다.
- 재촬영 요청을 받은 경우 새 사진을 제출한다.

## 5. 전체 처리 흐름

### 5.1 관리자 등록 흐름

1. 관리자가 회원가입 또는 로그인한다.
2. 그룹명을 입력해 그룹을 생성한다.
3. 서버가 고유한 6자리 초대 코드를 발급한다.
4. 관리자가 자연어 업무 요구사항을 입력한다.
5. 서버가 AI의 `POST /v1/tasks/generate`를 호출한다.
6. AI가 1~20개의 `PHOTO` 또는 `CHECK` 업무를 반환한다.
7. 관리자가 제목, 안내, 완료 방식, 검증 기준을 수정한다.
8. 필요한 `PHOTO` 업무에 참고용 기준 사진을 첨부한다.
9. 관리자가 담당자, 시간대, 반복 규칙을 설정한다.
10. 서버가 업무 템플릿, 하위 업무, 반복 일정을 저장한다.
11. 스케줄러 또는 수동 API가 특정 날짜의 실제 업무 배정을 생성한다.

### 5.2 알바생 수행 흐름

1. 알바생이 로그인하고 그룹의 오늘 업무를 조회한다.
2. `CHECK` 업무는 수행 후 완료 버튼을 누른다.
3. `PHOTO` 업무는 사진을 촬영해 multipart로 제출한다.
4. 서버가 수행 가능 시간, 파일 형식, 크기, 중복 해시를 검사한다.
5. 서버가 S3에 사진을 저장하고 SHA-256과 presigned URL을 AI에 전달한다.
6. `PASS`이면 업무를 완료 처리한다.
7. `RETAKE`이면 사유와 재촬영 방법을 반환한다.
8. AI 장애가 재시도 후에도 계속되면 제출 사진을 유지하고 검사 지연으로 표시한다.

## 6. 아키텍처

패키지는 기능 단위로 나누고 각 기능은 controller, service, domain, repository, dto 계층을 가진다.

- `member`: 회원가입, 로그인, 역할
- `group`: 그룹 생성, 가입, 소속 확인
- `task`: AI 초안, 업무 템플릿, 하위 업무
- `schedule`: 반복 규칙과 날짜별 배정 생성
- `assignment`: 업무 조회, 상태 전이, 직접 완료
- `attempt`: 사진 제출, AI 검증, 제출 이력
- `storage`: 파일 저장 포트와 S3 구현
- `ai`: AI 호출 포트와 HTTP 구현
- `global`: 공통 응답, 예외, 설정

외부 연동은 다음 포트로 격리한다.

```java
public interface AiTaskClient {
    List<GeneratedTask> generateTasks(String message);
    PhotoCheckResult checkPhoto(PhotoCheckCommand command);
}

public interface FileStorage {
    StoredFile store(StorageCommand command);
    URI createReadUrl(String objectKey, Duration validFor);
}
```

## 7. 도메인 모델

### 7.1 Member

- `id`: bigint PK
- `nickname`: varchar, unique, not null
- `role`: `MANAGER | WORKER`
- 생성 및 수정 시각

### 7.2 WorkGroup

`GROUP` 예약어 충돌을 피하기 위해 엔티티와 테이블 이름은 `WorkGroup`, `work_groups`를 사용한다.

- `id`: bigint PK
- `name`: varchar, not null
- `inviteCode`: char(6), unique, not null
- `ownerId`: Member FK
- 생성 및 수정 시각

### 7.3 GroupMember

- `id`: bigint PK
- `groupId`: WorkGroup FK
- `memberId`: Member FK
- `groupRole`: `MANAGER | WORKER`
- `(groupId, memberId)` unique

### 7.4 TaskTemplate

- `id`: bigint PK
- `groupId`: WorkGroup FK
- `creatorId`: Member FK
- `title`: 1~80자
- `sourceMessage`: 1~2,000자
- `active`: boolean
- 생성 및 수정 시각

### 7.5 TaskItemTemplate

- `id`: bigint PK
- `taskTemplateId`: TaskTemplate FK
- `sequence`: 1 이상
- `title`: 1~80자
- `instruction`: 1~500자
- `completionType`: `PHOTO | CHECK`
- `verificationRule`: PHOTO는 1~1,000자, CHECK는 null
- `referenceImageKey`: nullable
- `(taskTemplateId, sequence)` unique

### 7.6 TaskSchedule

- `id`: bigint PK
- `taskTemplateId`: TaskTemplate FK
- `assigneeId`: Member FK, nullable
- `startDate`: 시작일
- `endDate`: 종료일, nullable
- `startTime`: 시작 시각
- `endTime`: 종료 시각
- `recurrenceType`: `ONCE | DAILY | WEEKLY`
- `daysOfWeek`: WEEKLY에서 하나 이상의 요일
- `earlyAllowanceMinutes`: 0 이상
- `lateAllowanceMinutes`: 0 이상
- `active`: boolean

`endTime`이 `startTime`보다 이르면 다음 날 종료되는 야간 업무로 간주한다.

### 7.7 TaskAssignment

- `id`: bigint PK
- `scheduleId`: TaskSchedule FK
- `taskItemTemplateId`: TaskItemTemplate FK
- `assigneeId`: Member FK, nullable
- `scheduledDate`: 업무 기준일
- `availableFrom`: 수행 가능 시작 시각
- `dueAt`: 수행 가능 종료 시각
- `status`: `PENDING | VERIFYING | RETAKE_REQUIRED | COMPLETED | VERIFICATION_DELAYED | EXPIRED`
- `completedAt`: nullable
- `version`: 낙관적 잠금
- `(scheduleId, taskItemTemplateId, scheduledDate)` unique

### 7.8 TaskAttempt

- `id`: bigint PK
- `assignmentId`: TaskAssignment FK
- `submitterId`: Member FK
- `attemptNumber`: 1 이상
- `status`: `VERIFYING | PASS | RETAKE | DELAYED`
- `reason`: 최대 500자, nullable
- `fixMessage`: RETAKE일 때 최대 500자
- `submittedAt`: 제출 시각
- `(assignmentId, attemptNumber)` unique

### 7.9 TaskPhoto

- `id`: bigint PK
- `attemptId`: TaskAttempt FK, unique
- `groupId`: WorkGroup FK
- `objectKey`: S3 키
- `mimeType`: JPEG, PNG, WebP
- `sizeBytes`: 1~10MB
- `sha256`: 64자리 소문자 16진수
- `(groupId, sha256)` unique

## 8. 상태 전이

허용되는 배정 상태 전이는 다음과 같다.

- `PENDING -> COMPLETED`: CHECK 완료 또는 PHOTO PASS
- `PENDING -> VERIFYING`: 첫 사진 제출
- `RETAKE_REQUIRED -> VERIFYING`: 재촬영 제출
- `VERIFICATION_DELAYED -> VERIFYING`: 관리자 재검사
- `VERIFYING -> COMPLETED`: AI PASS
- `VERIFYING -> RETAKE_REQUIRED`: AI RETAKE
- `VERIFYING -> VERIFICATION_DELAYED`: AI 재시도 실패
- `PENDING | RETAKE_REQUIRED -> EXPIRED`: 수행 허용 시간 경과

완료 및 만료 상태에서는 새 제출을 허용하지 않는다.

## 9. 반복 일정과 업무 생성

- `ONCE`: `startDate`에 한 번 생성한다.
- `DAILY`: 시작일부터 종료일까지 매일 생성한다.
- `WEEKLY`: 지정 요일에만 생성한다.
- 매일 자정에 다음 날 업무를 생성한다.
- 데모용 API는 지정 날짜의 업무를 즉시 생성한다.
- DB unique 제약과 서비스의 존재 확인을 함께 사용해 중복 생성을 막는다.
- 담당자가 null인 배정은 해당 그룹의 WORKER가 수행할 수 있다.
- 수행 시점의 그룹 소속을 검사한다.

## 10. 사진 처리와 AI 판정

### 10.1 입력 검증

- 허용 MIME: `image/jpeg`, `image/png`, `image/webp`
- 크기: 1바이트 이상 10MB 이하
- SHA-256: 서버에서 파일 바이트로 계산
- 동일 그룹의 기존 `TaskPhoto.sha256`과 중복되면 제출 거절
- 클라이언트가 제공한 MIME만 신뢰하지 않고 파일 시그니처도 확인

### 10.2 저장과 호출

1. 사진을 검증한다.
2. S3 키를 생성해 원본을 업로드한다.
3. 읽기 전용 5분 presigned URL을 만든다.
4. AI 사진 검사 API를 호출한다.
5. 판정과 사유를 제출 이력에 저장한다.

S3 객체 키:

```text
groups/{groupId}/assignments/{assignmentId}/attempts/{uuid}.{extension}
groups/{groupId}/templates/{templateId}/references/{uuid}.{extension}
```

DB 저장이 실패한 경우 이미 업로드한 새 객체 삭제를 시도한다. 삭제 실패는 로그로 남기되 원래 예외를 유지한다.

## 11. 외부 AI API

공통 설정:

```text
AI_BASE_URL
AI_SERVICE_TOKEN
AI_CONNECT_TIMEOUT_SECONDS=5
AI_READ_TIMEOUT_SECONDS=60
```

모든 요청은 `Authorization: Bearer <service-token>`과 JSON Content-Type을 사용한다. 회원의 닉네임, 이메일 등 개인정보는 전송하지 않는다.

### 11.1 태스크 생성

```http
POST {AI_BASE_URL}/v1/tasks/generate
```

요청:

```json
{
  "message": "오픈 전에 조명을 켜고 POS기 전원과 카운터 정리를 확인해야 해"
}
```

응답의 `tasks`는 1~20개다. 각 항목은 `title`, `instruction`, `completionType`, `rule`을 포함한다. PHOTO는 rule이 필수이고 CHECK는 null이다.

### 11.2 사진 검사

```http
POST {AI_BASE_URL}/v1/attempts/check
```

요청에는 태스크의 제목, 안내, 규칙과 사진의 MIME, 크기, SHA-256, HTTPS 임시 URL을 포함한다.

응답:

- `PASS`: reason 필수, fix 없음
- `RETAKE`: reason과 fix 필수

### 11.3 외부 오류 처리

- `400 INVALID_REQUEST`: 내부 요청 구성 오류로 변환
- `401 UNAUTHORIZED`: AI 서비스 설정 오류로 변환
- `422 PHOTO_UNAVAILABLE`: 새 presigned URL로 같은 검사를 한 번 재요청
- `503 AI_UNAVAILABLE` 또는 제한 시간 초과: 같은 요청을 한 번 재시도
- 재시도 실패: 제출을 보존하고 `VERIFICATION_DELAYED`
- 모델 공급자의 원문 오류는 클라이언트 응답에 노출하지 않는다.

## 12. API 명세

모든 응답은 기존 `ApiResponse` 형식을 사용한다.

```json
{
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {}
}
```

### 12.1 회원

#### `POST /api/v1/members`

요청:

```json
{
  "nickname": "야간알바",
  "role": "WORKER"
}
```

닉네임 중복 시 409를 반환한다.

#### `POST /api/v1/members/login`

요청:

```json
{
  "nickname": "야간알바"
}
```

응답은 `memberId`, `nickname`, `role`을 포함한다.

### 12.2 그룹

- `POST /api/v1/groups`: `managerId`, `name`으로 생성
- `POST /api/v1/groups/join`: `memberId`, `inviteCode`로 가입
- `GET /api/v1/members/{memberId}/groups`: 내 그룹 조회
- `GET /api/v1/groups/{groupId}/members?requesterId={id}`: 구성원 조회

### 12.3 AI 초안

#### `POST /api/v1/groups/{groupId}/task-drafts`

요청:

```json
{
  "managerId": 1,
  "message": "밤 11시에 POS 전원을 확인하고 매장 바닥을 청소해줘"
}
```

응답은 서버 메모리의 만료 가능한 `draftId`와 생성 항목을 반환한다. 초안은 영속화하지 않으며 최종 등록 요청에서 전체 항목을 다시 보낸다. 서버 재시작 후 draftId가 사라져도 최종 등록은 전체 항목 검증으로 진행할 수 있으므로 draftId는 추적 정보로만 사용한다.

### 12.4 템플릿

- `POST /api/v1/groups/{groupId}/task-templates`: multipart의 JSON `request`와 선택 기준 사진으로 등록
- `GET /api/v1/task-templates/{templateId}?memberId={id}`: 상세 조회
- `GET /api/v1/groups/{groupId}/task-templates?memberId={id}`: 목록 조회
- `PATCH /api/v1/task-templates/{templateId}`: 제목, 활성 여부, 일정 수정
- `DELETE /api/v1/task-templates/{templateId}?managerId={id}`: 소프트 삭제

등록 요청에는 다음 일정 정보가 포함된다.

```json
{
  "managerId": 1,
  "draftId": "optional-uuid",
  "title": "야간 POS 및 매장 정리",
  "sourceMessage": "밤 11시에 POS 전원을 확인하고 매장 바닥을 청소해줘",
  "assigneeId": 2,
  "startDate": "2026-08-20",
  "endDate": "2026-09-30",
  "startTime": "23:00:00",
  "endTime": "23:40:00",
  "recurrenceType": "WEEKLY",
  "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "earlyAllowanceMinutes": 10,
  "lateAllowanceMinutes": 20,
  "items": []
}
```

### 12.5 배정

- `POST /api/v1/groups/{groupId}/assignments/generate`: `managerId`, `targetDate`로 수동 생성
- `GET /api/v1/workers/{workerId}/assignments?date={date}`: 알바생 업무 조회
- `GET /api/v1/groups/{groupId}/assignments?managerId={id}&date={date}`: 그룹 현황
- `GET /api/v1/assignments/{assignmentId}?memberId={id}`: 업무 상세

### 12.6 수행과 제출

- `POST /api/v1/assignments/{assignmentId}/check`: `workerId`로 CHECK 완료
- `POST /api/v1/assignments/{assignmentId}/photo-attempts`: multipart `workerId`, `photo`
- `GET /api/v1/assignments/{assignmentId}/attempts?memberId={id}`: 제출 이력
- `POST /api/v1/attempts/{attemptId}/retry`: 관리자 지연 검사 재처리

## 13. 오류 코드

- `INVALID_INPUT_VALUE`: 400
- `INVALID_JSON_FORMAT`: 400
- `INVALID_ROLE`: 400
- `INVALID_COMPLETION_TYPE`: 400
- `INVALID_SCHEDULE`: 400
- `INVALID_PHOTO`: 400
- `MEMBER_NOT_FOUND`: 404
- `GROUP_NOT_FOUND`: 404
- `TEMPLATE_NOT_FOUND`: 404
- `ASSIGNMENT_NOT_FOUND`: 404
- `ATTEMPT_NOT_FOUND`: 404
- `DUPLICATE_NICKNAME`: 409
- `ALREADY_GROUP_MEMBER`: 409
- `DUPLICATE_PHOTO`: 409
- `ASSIGNMENT_ALREADY_COMPLETED`: 409
- `TASK_NOT_AVAILABLE`: 409
- `GROUP_ACCESS_DENIED`: 403
- `MANAGER_REQUIRED`: 403
- `WORKER_REQUIRED`: 403
- `AI_INVALID_REQUEST`: 502
- `AI_UNAUTHORIZED`: 502
- `AI_UNAVAILABLE`: 503
- `STORAGE_UNAVAILABLE`: 503

## 14. 설정

```text
DB_URL
DB_USERNAME
DB_PASSWORD
AI_BASE_URL
AI_SERVICE_TOKEN
AI_CONNECT_TIMEOUT_SECONDS=5
AI_READ_TIMEOUT_SECONDS=60
AWS_REGION
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
S3_BUCKET
S3_PRESIGNED_URL_MINUTES=5
```

테스트와 로컬 실행에서는 `ai.stub-enabled`와 `storage.local-enabled` 프로필을 제공해 외부 계정 없이 전체 흐름을 실행할 수 있게 한다. 운영 프로필은 HTTP AI 어댑터와 S3 어댑터를 사용한다.

## 15. 데모 실행 시나리오

1. MANAGER 회원을 가입시키고 로그인한다.
2. WORKER 회원을 가입시키고 로그인한다.
3. 관리자가 그룹을 생성하고 6자리 초대 코드를 확인한다.
4. 알바생이 초대 코드로 그룹에 가입한다.
5. 관리자가 자연어로 업무 초안을 생성한다.
6. 생성된 PHOTO와 CHECK 항목을 확인하고 필요하면 수정한다.
7. 관리자가 담당자, 야간 시간, 반복 요일을 지정해 템플릿을 등록한다.
8. 관리자가 오늘 날짜를 대상으로 수동 생성 API를 호출한다.
9. 알바생이 오늘 업무를 조회한다.
10. CHECK 업무를 완료한다.
11. PHOTO 업무에 기준을 충족하지 않는 사진을 제출하고 RETAKE를 확인한다.
12. 올바른 사진을 제출하고 PASS와 COMPLETED를 확인한다.
13. 같은 사진을 다른 PHOTO 업무에 제출해 DUPLICATE_PHOTO를 확인한다.
14. 관리자가 그룹 현황과 제출 이력을 조회한다.

## 16. 테스트 전략

### 16.1 도메인 단위 테스트

- 초대 코드가 6자리 숫자인지 검증
- 반복 일정의 날짜 포함 여부
- 자정을 넘는 야간 시간 계산
- 수행 가능 시간 검사
- 허용된 배정 상태 전이와 역행 방지
- PHOTO와 CHECK 검증 규칙

### 16.2 서비스 테스트

- 그룹 생성과 가입 중복 방지
- AI 초안 응답 검증
- 날짜별 배정 생성과 멱등성
- 직접 담당 업무와 미지정 업무 조회
- CHECK 직접 완료
- 사진 중복 차단
- PASS, RETAKE, DELAYED 처리
- 422와 503 재시도

### 16.3 API 통합 테스트

- 회원가입부터 업무 완료까지 성공 흐름
- 필수 값과 역할 오류 응답
- 멤버가 아닌 사용자의 그룹 접근 차단
- 잘못된 파일 형식과 크기 차단

### 16.4 외부 어댑터 테스트

- HTTP 요청 헤더와 JSON 계약
- AI 오류 응답 매핑
- S3 객체 키와 presigned URL

## 17. 완료 기준

- 전체 API가 공통 응답 형식을 사용한다.
- 외부 계정 없이 테스트 프로필에서 데모 전체 흐름을 실행할 수 있다.
- 운영 프로필에서 PDF 명세의 AI 서버와 S3를 설정으로 연결할 수 있다.
- 핵심 상태 전이와 반복 생성이 자동 테스트로 검증된다.
- API 사용 방법과 데모 호출 순서가 README 또는 별도 문서에 제공된다.
