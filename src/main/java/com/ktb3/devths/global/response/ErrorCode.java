package com.ktb3.devths.global.response;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	// 400 Bad Request
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다"),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
	GOOGLE_TOKEN_EXCHANGE_FAILED(HttpStatus.BAD_REQUEST, "Google 토큰 교환에 실패했습니다"),
	GOOGLE_USER_INFO_FETCH_FAILED(HttpStatus.BAD_REQUEST, "Google 사용자 정보 조회에 실패했습니다"),
	INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 인증 코드입니다"),
	INVALID_FILE_REFERENCE(HttpStatus.BAD_REQUEST, "유효하지 않은 파일 참조입니다"),
	INVALID_EVENT_TIME(HttpStatus.BAD_REQUEST, "일정 시작 시간은 종료 시간보다 이전이어야 합니다"),
	INTERVIEW_COMPLETED(HttpStatus.BAD_REQUEST, "면접이 종료되었습니다. 새 면접을 시작해 주세요."),
	INTERVIEW_EVALUATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "종료된 면접은 평가할 수 없습니다."),
	SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인을 팔로우할 수 없습니다"),
	SELF_UNFOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인을 언팔로우할 수 없습니다"),
	SELF_CHAT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인과의 채팅방은 생성할 수 없습니다"),

	// 401 Unauthorized
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
	EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다"),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다"),
	EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰입니다"),
	INVALID_TEMP_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 임시 토큰입니다"),
	EXPIRED_TEMP_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 임시 토큰입니다"),
	REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용된 리프레시 토큰입니다"),
	GOOGLE_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Google 토큰이 만료되었습니다"),
	GOOGLE_TOKEN_REFRESH_FAILED(HttpStatus.UNAUTHORIZED, "Google 토큰 갱신에 실패했습니다"),

	// 403 Forbidden
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
	WITHDRAWN_USER(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다"),
	AI_CHATROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 AI 채팅방에 접근 권한이 없습니다"),
	ASYNC_TASK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 작업에 접근 권한이 없습니다"),
	GOOGLE_CALENDAR_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Google Calendar 접근 권한이 없습니다"),
	GOOGLE_TASKS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Google Tasks 접근 권한이 없습니다"),
	EVENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 일정에 접근 권한이 없습니다"),
	POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 게시글에 접근 권한이 없습니다"),
	COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 댓글에 접근 권한이 없습니다"),
	CHATROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 채팅방에 접근 권한이 없습니다"),

	// 404 Not Found
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
	AI_CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 AI 채팅방을 찾을 수 없습니다"),
	ASYNC_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "비동기 작업을 찾을 수 없습니다"),
	INTERVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "면접을 찾을 수 없습니다"),
	EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 일정을 찾을 수 없습니다"),
	TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 할 일을 찾을 수 없습니다"),
	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 게시글을 찾을 수 없습니다"),
	COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 댓글을 찾을 수 없습니다"),
	CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 채팅방을 찾을 수 없습니다"),

	// 409 Conflict
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
	DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다"),
	DUPLICATE_ANALYSIS(HttpStatus.CONFLICT, "이미 진행 중인 분석 작업이 있습니다"),
	INTERVIEW_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 면접이 있습니다"),
	INTERVIEW_TYPE_MISMATCH(HttpStatus.CONFLICT, "진행 중인 면접과 다른 타입의 면접은 시작할 수 없습니다"),

	// 429 Too Many Requests
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "API 호출 제한을 초과했습니다"),

	// 500 Internal Server Error
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
	ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "분석 처리 중 오류가 발생했습니다"),
	GOOGLE_CALENDAR_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Google Calendar 일정 추가에 실패했습니다"),

	// 503 Service Unavailable
	FASTAPI_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버 연결에 실패했습니다"),
	GOOGLE_CALENDAR_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Google Calendar 서비스를 일시적으로 사용할 수 없습니다"),
	GOOGLE_TASKS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Google Tasks 서비스를 일시적으로 사용할 수 없습니다"),

	// 504 Gateway Timeout
	FASTAPI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버 응답 시간이 초과되었습니다");

	private final HttpStatus status;
	private final String message;
}
