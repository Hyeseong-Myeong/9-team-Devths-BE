package com.ktb3.devths.ai.chatbot.dto.request;

import jakarta.validation.constraints.NotNull;

public record InterviewEndRequest(
	@NotNull(message = "면접 ID는 필수입니다")
	Long interviewId
) {
}
