package com.ktb3.devths.chat.dto.response;

import java.time.LocalDateTime;

public record ChatRoomSummaryResponse(
	Long roomId,
	String title,
	String profileImage,
	String lastMessageContent,
	LocalDateTime lastMessageAt,
	int currentCount,
	String tag
) {
}
