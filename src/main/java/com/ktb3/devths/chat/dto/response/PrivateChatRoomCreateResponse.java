package com.ktb3.devths.chat.dto.response;

import java.time.LocalDateTime;

public record PrivateChatRoomCreateResponse(
	Long roomId,
	boolean isNew,
	String type,
	String title,
	String inviteCode,
	LocalDateTime createdAt
) {
}
