-- 편의점 업무 관리 AI 에이전트 데모 데이터
-- 대상: MySQL 8 / Amazon RDS for MySQL, database = moamoa
-- 전제: Spring Boot를 한 번 실행하여 Hibernate가 테이블을 생성한 뒤 실행한다.
-- 주의: 910001~910003 ID는 이 파일이 소유하는 데모 전용 범위다.

USE moamoa;
SET NAMES utf8mb4;
SET time_zone = '+09:00';

START TRANSACTION;

-- 반복 실행을 위해 이 파일이 만든 데이터만 외래 키의 역순으로 정리한다.
DELETE FROM task_photos WHERE id IN (910001, 910002, 910003);
DELETE FROM task_attempts WHERE id IN (910001, 910002, 910003);
DELETE FROM task_assignments WHERE id IN (910001, 910002, 910003);
DELETE FROM task_schedule_days WHERE schedule_id IN (910001, 910002);
DELETE FROM task_schedules WHERE id IN (910001, 910002);
DELETE FROM task_item_templates WHERE id IN (910001, 910002, 910003);
DELETE FROM task_templates WHERE id IN (910001, 910002);
DELETE FROM group_members WHERE id IN (910001, 910002, 910003);
DELETE FROM work_groups WHERE id = 910001;
DELETE FROM members WHERE id IN (910001, 910002, 910003);

-- 1. 회원: 관리자 1명, 알바생 2명
INSERT INTO members (id, nickname, role, created_at, updated_at)
VALUES
    (910001, 'demo_manager', 'MANAGER', NOW(), NOW()),
    (910002, 'demo_worker_night', 'WORKER', NOW(), NOW()),
    (910003, 'demo_worker_backup', 'WORKER', NOW(), NOW());

-- 2. 그룹과 소속
INSERT INTO work_groups (id, name, invite_code, owner_id, created_at, updated_at)
VALUES (910001, '모아모아 편의점 야간조', '112233', 910001, NOW(), NOW());

INSERT INTO group_members (id, group_id, member_id, group_role, created_at, updated_at)
VALUES
    (910001, 910001, 910001, 'MANAGER', NOW(), NOW()),
    (910002, 910001, 910002, 'WORKER', NOW(), NOW()),
    (910003, 910001, 910003, 'WORKER', NOW(), NOW());

-- 3. 특정 알바생에게 직접 배정할 야간 업무 템플릿
INSERT INTO task_templates
    (id, group_id, creator_id, title, source_message, active, created_at, updated_at)
VALUES
    (910001, 910001, 910001,
     '야간 POS 및 매장 정리',
     '야간 근무자가 POS 전원을 확인하고 매장 바닥을 청소해줘',
     TRUE, NOW(), NOW());

INSERT INTO task_item_templates
    (id, task_template_id, sequence_no, title, instruction, completion_type,
     verification_rule, reference_image_key, reference_image_mime_type,
     reference_image_size_bytes, reference_image_sha256)
VALUES
    (910001, 910001, 1,
     'POS 전원 확인',
     'POS 화면이 켜진 상태가 선명하게 보이도록 촬영해 주세요.',
     'PHOTO',
     '사진에서 POS 화면이 켜져 있고 정상 화면이 표시되어야 한다.',
     NULL, NULL, NULL, NULL),
    (910002, 910001, 2,
     '매장 바닥 청소',
     '매장 바닥 청소를 마친 뒤 완료 버튼을 눌러 주세요.',
     'CHECK',
     NULL,
     NULL, NULL, NULL, NULL);

-- 매일 반복되며 demo_worker_night에게 직접 배정한다.
INSERT INTO task_schedules
    (id, task_template_id, assignee_id, start_date, end_date, start_time, end_time,
     recurrence_type, early_allowance_minutes, late_allowance_minutes, active,
     created_at, updated_at)
VALUES
    (910001, 910001, 910002,
     CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY),
     '22:00:00', '06:00:00', 'DAILY', 10, 20, TRUE, NOW(), NOW());

-- 4. 담당자 미지정 근무 시간대 공용 업무
INSERT INTO task_templates
    (id, group_id, creator_id, title, source_message, active, created_at, updated_at)
VALUES
    (910002, 910001, 910001,
     '야간 공용 점검',
     '야간 근무자가 냉장 진열대 온도를 확인해줘',
     TRUE, NOW(), NOW());

INSERT INTO task_item_templates
    (id, task_template_id, sequence_no, title, instruction, completion_type,
     verification_rule, reference_image_key, reference_image_mime_type,
     reference_image_size_bytes, reference_image_sha256)
VALUES
    (910003, 910002, 1,
     '냉장 진열대 온도 확인',
     '냉장 진열대 온도가 정상 범위인지 확인한 뒤 완료해 주세요.',
     'CHECK',
     NULL,
     NULL, NULL, NULL, NULL);

INSERT INTO task_schedules
    (id, task_template_id, assignee_id, start_date, end_date, start_time, end_time,
     recurrence_type, early_allowance_minutes, late_allowance_minutes, active,
     created_at, updated_at)
VALUES
    (910002, 910002, NULL,
     CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY),
     '22:00:00', '06:00:00', 'DAILY', 10, 20, TRUE, NOW(), NOW());

-- 5. 오늘 바로 시연할 수 있는 실제 업무 배정
-- SQL 실행 시각과 관계없이 오늘 하루 동안 수행할 수 있게 넓은 시간 창을 사용한다.
INSERT INTO task_assignments
    (id, schedule_id, task_item_template_id, assignee_id, scheduled_date,
     available_from, due_at, status, completed_at, version, created_at, updated_at)
VALUES
    (910001, 910001, 910001, 910002, CURRENT_DATE,
     TIMESTAMP(CURRENT_DATE, '00:00:00'), TIMESTAMP(CURRENT_DATE, '23:59:59'),
     'PENDING', NULL, 0, NOW(), NOW()),
    (910002, 910001, 910002, 910002, CURRENT_DATE,
     TIMESTAMP(CURRENT_DATE, '00:00:00'), TIMESTAMP(CURRENT_DATE, '23:59:59'),
     'PENDING', NULL, 0, NOW(), NOW()),
    (910003, 910002, 910003, NULL, CURRENT_DATE,
     TIMESTAMP(CURRENT_DATE, '00:00:00'), TIMESTAMP(CURRENT_DATE, '23:59:59'),
     'PENDING', NULL, 0, NOW(), NOW());

COMMIT;

-- 데모 확인용 조회
SELECT id, nickname, role FROM members WHERE id BETWEEN 910001 AND 910003;
SELECT id, name, invite_code FROM work_groups WHERE id = 910001;
SELECT id, title, completion_type FROM task_item_templates WHERE id BETWEEN 910001 AND 910003;
SELECT id, task_item_template_id, assignee_id, scheduled_date, status, available_from, due_at
FROM task_assignments
WHERE id BETWEEN 910001 AND 910003
ORDER BY id;
