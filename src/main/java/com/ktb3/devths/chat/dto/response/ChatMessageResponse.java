package com.ktb3.devths.chat.dto.response;

import java.time.LocalDateTime;

public record ChatMessageResponse(
	Long messageId,
	Sender sender,
	String type,
	String content,
	String s3Key,
	LocalDateTime createdAt,
	boolean isDeleted
) {

	public record Sender(Long userId, String nickname, String profileImage) {
	}
}
