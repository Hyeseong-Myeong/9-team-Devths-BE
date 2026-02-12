package com.ktb3.devths.global.ratelimit.domain.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiType {
	GOOGLE_CALENDAR("google.calendar", "Google Calendar API (쓰기 작업)"),
	GOOGLE_TASKS("google.tasks", "Google Tasks API (쓰기 작업)"),
	FASTAPI_ANALYSIS("fastapi.analysis", "FastAPI 분석 요청"),
	FASTAPI_CHAT("fastapi.chat", "FastAPI 챗봇 메시지"),
	FASTAPI_EVALUATION("fastapi.evaluation", "FastAPI 면접 평가"),
	GOOGLE_OAUTH("google.oauth", "Google OAuth2 로그인"),
	AUTH_TOKEN("auth.token", "인증/토큰 처리"),
	FILE_PRESIGNED("file.presigned", "파일 Presigned URL 발급"),
	FILE_ATTACHMENT("file.attachment", "파일 등록/삭제"),
	BOARD_WRITE("board.write", "게시판 쓰기 작업"),
	SOCIAL_ACTION("social.action", "소셜 액션");

	private final String key;
	private final String description;
}
