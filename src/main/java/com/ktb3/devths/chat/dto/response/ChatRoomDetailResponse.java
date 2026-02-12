package com.ktb3.devths.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomDetailResponse(
	Long roomId,
	String type,
	String title,
	boolean isAlarmOn,
	String roomName,
	String inviteCode,
	LocalDateTime createdAt,
	List<RecentImage> recentImages
) {
	public record RecentImage(
		Long attachmentId,
		String s3Key,
		String originalName,
		LocalDateTime createdAt
	) {
	}
}
