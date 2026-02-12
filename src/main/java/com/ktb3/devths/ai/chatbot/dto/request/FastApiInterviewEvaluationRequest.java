package com.ktb3.devths.ai.chatbot.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiInterviewEvaluationRequest(
	String name,
	Value value
) {
	public record Value(
		List<ContextEntry> context,
		boolean retry,
		@JsonProperty("room_id")
		Long roomId,
		@JsonProperty("session_id")
		Long sessionId,
		@JsonProperty("user_id")
		Long userId,
		@JsonProperty("interview_type")
		String interviewType
	) {
	}

	public record ContextEntry(
		String question,
		String answer
	) {
	}
}
