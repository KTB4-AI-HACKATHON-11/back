package com.ktb.hackathon.team11.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
  INVALID_JSON_FORMAT(HttpStatus.BAD_REQUEST, "요청 JSON 형식이 올바르지 않습니다."),
  INVALID_ROLE(HttpStatus.BAD_REQUEST, "역할이 올바르지 않습니다."),
  INVALID_COMPLETION_TYPE(HttpStatus.BAD_REQUEST, "업무 완료 방식이 올바르지 않습니다."),
  INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "업무 일정이 올바르지 않습니다."),
  INVALID_DUE_AT(HttpStatus.BAD_REQUEST, "마감 일시가 올바르지 않습니다."),
  INVALID_STORE_INFO_INPUT(HttpStatus.BAD_REQUEST, "매장 정보 입력값이 올바르지 않습니다."),
  INVALID_PHOTO(HttpStatus.BAD_REQUEST, "사진 형식 또는 크기가 올바르지 않습니다."),
  PHOTO_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진 용량은 10MB를 초과할 수 없습니다."),
  REFERENCE_PHOTO_REQUIRED(HttpStatus.BAD_REQUEST, "PHOTO 업무에는 기준 사진이 필요합니다."),
  VERIFICATION_RULE_REQUIRED(HttpStatus.BAD_REQUEST, "사진 검증 항목의 검증 기준을 입력해주세요."),
  INVALID_REFERENCE_PHOTO_INDEX(HttpStatus.BAD_REQUEST, "기준 사진 인덱스가 올바르지 않습니다."),
  INVALID_PUSH_SUBSCRIPTION(HttpStatus.BAD_REQUEST, "브라우저 알림 구독 정보가 올바르지 않습니다."),
  SESSION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
  SESSION_MEMBER_MISMATCH(HttpStatus.FORBIDDEN, "로그인한 회원과 요청 회원이 일치하지 않습니다."),
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
  GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."),
  STORE_INFO_NOT_FOUND(HttpStatus.NOT_FOUND, "매장 정보를 찾을 수 없습니다."),
  TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "업무 템플릿을 찾을 수 없습니다."),
  TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "태스크를 찾을 수 없습니다."),
  CHECKLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "태스크에 속한 체크리스트를 찾을 수 없습니다."),
  ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "배정 업무를 찾을 수 없습니다."),
  ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "제출 이력을 찾을 수 없습니다."),
  MANAGER_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "매니저 확인 요청을 찾을 수 없습니다."),
  DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
  ALREADY_GROUP_MEMBER(HttpStatus.CONFLICT, "이미 가입한 그룹입니다."),
  WORKER_NOT_IN_GROUP(HttpStatus.CONFLICT, "해당 그룹에 가입된 알바생이 아닙니다."),
  DUPLICATE_PHOTO(HttpStatus.CONFLICT, "이미 인증에 사용된 사진입니다."),
  ASSIGNMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 업무입니다."),
  TASK_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 수행할 수 없는 업무입니다."),
  TASK_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 수행이 시작된 태스크는 수정할 수 없습니다."),
  MANAGER_REVIEW_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 매니저 확인을 요청할 수 없습니다."),
  MANAGER_REVIEW_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 처리된 매니저 확인 요청입니다."),
  GROUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "그룹에 접근할 수 없습니다."),
  MANAGER_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
  VERIFICATION_SETTINGS_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "검증 기준을 수정할 권한이 없습니다."),
  WORKER_REQUIRED(HttpStatus.FORBIDDEN, "알바생 권한이 필요합니다."),
  AI_INVALID_REQUEST(HttpStatus.BAD_GATEWAY, "AI 요청 구성에 실패했습니다."),
  AI_UNAUTHORIZED(HttpStatus.BAD_GATEWAY, "AI 서비스 설정을 확인해 주세요."),
  TASK_GENERATION_REJECTED(
      HttpStatus.UNPROCESSABLE_ENTITY, "입력에서 생성할 수 있는 구체적인 업무를 찾지 못했습니다."),
  PHOTO_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "AI가 사진을 불러올 수 없습니다."),
  AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스를 일시적으로 사용할 수 없습니다."),
  STORE_INFO_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "매장 정보는 최대 60,000자까지 등록할 수 있습니다."),
  STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "파일 저장소를 일시적으로 사용할 수 없습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String message;
}
