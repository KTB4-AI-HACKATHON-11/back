# 편의점 업무 에이전트 API

기본 경로는 `/api/v1`이며 모든 응답은 `code`, `message`, `data`를 갖는다. 데모 인증은 로그인 응답의 `memberId`를 이후 요청에 전달하는 방식이다.

## 회원과 그룹

| Method | Path | 설명 |
|---|---|---|
| POST | `/members` | `nickname`, `role`로 가입 |
| POST | `/members/login` | 닉네임으로 데모 로그인 |
| POST | `/groups` | `managerId`, `name`으로 그룹 생성 |
| POST | `/groups/join` | `memberId`, `inviteCode`로 가입 |
| GET | `/members/{memberId}/groups` | 내 그룹 목록 |
| GET | `/groups/{groupId}/members?requesterId=` | 그룹 구성원 |

역할 값은 `MANAGER`, `WORKER`다. 그룹 생성 응답의 `inviteCode`는 6자리 숫자다.

## 업무 생성

### AI 초안

```http
POST /api/v1/groups/{groupId}/task-drafts
Content-Type: application/json

{"managerId":1,"message":"POS 전원 확인 후 매장 바닥을 청소해줘"}
```

### 템플릿 등록

```http
POST /api/v1/groups/{groupId}/task-templates
Content-Type: application/json
```

```json
{
  "managerId": 1,
  "title": "야간 마감",
  "sourceMessage": "POS 전원 확인 후 매장 바닥을 청소해줘",
  "items": [
    {
      "title": "POS 전원 확인",
      "instruction": "POS 화면을 촬영해 주세요.",
      "completionType": "PHOTO",
      "verificationRule": "POS 화면이 켜져 있어야 한다."
    },
    {
      "title": "매장 바닥 청소",
      "instruction": "청소 후 완료해 주세요.",
      "completionType": "CHECK",
      "verificationRule": null
    }
  ]
}
```

### 반복 일정 등록

```http
POST /api/v1/task-templates/{templateId}/schedules
```

```json
{
  "managerId": 1,
  "assigneeId": 2,
  "startDate": "2026-08-19",
  "endDate": "2026-09-30",
  "startTime": "22:00:00",
  "endTime": "06:00:00",
  "recurrenceType": "DAILY",
  "daysOfWeek": [],
  "earlyAllowanceMinutes": 10,
  "lateAllowanceMinutes": 20
}
```

`recurrenceType`은 `ONCE`, `DAILY`, `WEEKLY`다. 종료 시각이 시작 시각보다 이르면 다음 날 종료되는 야간 근무로 계산한다.

PHOTO 하위 업무의 선택 기준 사진은 `POST /api/v1/task-items/{itemId}/reference-image`에 multipart의 `managerId`, `photo`로 등록한다. 기준 사진은 관리자 참고용이며 AI 판정에는 텍스트 `verificationRule`을 사용한다.

## 배정과 수행

| Method | Path | 설명 |
|---|---|---|
| POST | `/groups/{groupId}/assignments/generate` | 지정 날짜 배정 즉시 생성 |
| GET | `/workers/{workerId}/assignments?date=` | 알바생 업무 목록 |
| GET | `/groups/{groupId}/assignments?managerId=&date=` | 관리자 현황 |
| GET | `/assignments/{id}?memberId=` | 업무 상세 |
| POST | `/assignments/{id}/check` | CHECK 업무 완료 |
| POST | `/assignments/{id}/photo-attempts` | PHOTO 업무 제출 |
| GET | `/assignments/{id}/attempts?memberId=` | 제출 이력 |
| POST | `/attempts/{id}/retry?managerId=` | 지연 검사 재처리 |

사진 제출은 `multipart/form-data`의 `workerId`와 `photo`를 사용한다. JPEG, PNG, WebP만 허용하며 최대 크기는 10MB다.

## 운영 설정

```text
DB_URL, DB_USERNAME, DB_PASSWORD
AI_BACKEND_BASE_URL, AI_SERVICE_TOKEN
AI_STUB_ENABLED=false
STORAGE_LOCAL_ENABLED=false
AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, S3_BUCKET
```

AI HTTP 계약과 오류 정책은 `docs/superpowers/specs/2026-08-19-convenience-store-task-agent-design.md`를 따른다.
