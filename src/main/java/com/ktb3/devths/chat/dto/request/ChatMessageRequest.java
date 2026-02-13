package com.ktb3.devths.chat.dto.request;

public record ChatMessageRequest(
	Long roomId,
	String type,
	String content,
	String s3Key
) {
}
