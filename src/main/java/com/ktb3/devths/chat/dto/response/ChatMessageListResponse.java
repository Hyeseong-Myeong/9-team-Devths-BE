package com.ktb3.devths.chat.dto.response;

import java.util.List;

public record ChatMessageListResponse(
	List<ChatMessageResponse> messages,
	Long lastReadMsgId,
	Long nextCursor,
	boolean hasNext
) {
}
