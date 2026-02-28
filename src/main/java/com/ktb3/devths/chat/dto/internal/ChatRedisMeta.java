package com.ktb3.devths.chat.dto.internal;

public record ChatRedisMeta(
	String eventType,
	Long roomId,
	Long messageId,
	String chatSessionId
) {
}
