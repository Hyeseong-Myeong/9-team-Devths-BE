package com.ktb3.devths.chat.dto.internal;

import com.ktb3.devths.chat.dto.response.ChatRoomNotification;

public record ChatRedisNotificationEnvelope(
	ChatRedisNotificationMeta meta,
	ChatRedisTraceContext trace,
	ChatRoomNotification payload
) {
}
