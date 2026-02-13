package com.ktb3.devths.chat.dto.response;

import java.time.LocalDateTime;

public record ChatRoomNotification(
	Long roomId,
	String lastMessageContent,
	LocalDateTime lastMessageAt
) {
}
