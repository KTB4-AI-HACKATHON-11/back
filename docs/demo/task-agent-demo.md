# 데모 실행 가이드

## 1. 로컬 실행

기본 설정은 AI stub과 메모리 파일 저장소를 사용한다. MySQL만 준비하면 AWS와 AI 서버 없이 흐름을 시연할 수 있다.

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
$env:DB_PASSWORD='로컬-MySQL-비밀번호'
.\gradlew.bat bootRun
```

Base URL은 `http://localhost:8080/api/v1`이다.

## 2. 실행 순서

### 더미 데이터로 빠르게 시작

Spring Boot를 한 번 실행해 Hibernate가 `moamoa`에 테이블을 만든 다음 다음 SQL을 실행한다.

```powershell
mysql -h RDS_ENDPOINT -P 3306 -u moamoa_app -p moamoa `
  -e "source src/main/resources/db/demo-data.sql"
```

더미 계정과 고정 ID:

| 구분 | nickname | memberId |
|---|---|---:|
| 관리자 | `demo_manager` | 910001 |
| 야간 알바생 | `demo_worker_night` | 910002 |
| 대체 알바생 | `demo_worker_backup` | 910003 |

- 그룹 ID: `910001`
- 초대 코드: `112233`
- 오늘 PHOTO 배정 ID: `910001`
- 오늘 직접 배정 CHECK ID: `910002`
- 오늘 담당자 미지정 CHECK ID: `910003`
- SQL은 위 데모 ID만 정리하고 다시 생성하므로 반복 실행할 수 있다.
- 실제 기준 사진과 제출 사진은 S3 및 AI 연동을 확인할 수 있도록 API에서 직접 업로드한다.

1. `POST /members`로 점장을 `MANAGER`로 가입한다.
2. 같은 API로 알바생을 `WORKER`로 가입한다.
3. `POST /members/login` 응답의 두 `memberId`를 기록한다.
4. `POST /groups`로 그룹을 만들고 6자리 `inviteCode`를 확인한다.
5. `POST /groups/join`으로 알바생을 그룹에 가입시킨다.
6. `POST /groups/{groupId}/task-drafts`에 자연어 업무를 보낸다.
7. 반환된 항목을 수정해 `POST /groups/{groupId}/task-templates`로 등록한다.
8. `POST /task-templates/{templateId}/schedules`로 담당자와 반복 시간을 등록한다.
9. `POST /groups/{groupId}/assignments/generate`에 오늘 날짜를 보내 즉시 생성한다.
10. `GET /workers/{workerId}/assignments?date=오늘`로 업무를 확인한다.
11. CHECK 항목은 `POST /assignments/{id}/check`에 `{"workerId":2}`를 보낸다.
12. PHOTO 항목은 multipart로 제출한다.

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/assignments/1/photo-attempts" `
  -F "workerId=2" `
  -F "photo=@C:\demo\pos.jpg;type=image/jpeg"
```

13. `GET /groups/{groupId}/assignments?managerId=1&date=오늘`로 완료 상태를 확인한다.
14. `GET /assignments/{id}/attempts?memberId=1`로 AI 판정 사유를 확인한다.

## 3. 실제 AI와 S3 연결

```powershell
$env:AI_STUB_ENABLED='false'
$env:AI_BACKEND_BASE_URL='http://158.180.72.141:8890'
$env:AI_SERVICE_TOKEN='service-token'
$env:STORAGE_LOCAL_ENABLED='false'
$env:AWS_REGION='ap-northeast-2'
$env:S3_BUCKET='private-task-photo-bucket'
$env:AWS_ACCESS_KEY_ID='...'
$env:AWS_SECRET_ACCESS_KEY='...'
.\gradlew.bat bootRun
```

S3 버킷은 비공개로 유지한다. 서비스는 AI 검사 때만 짧은 수명의 읽기 전용 presigned URL을 생성한다.
