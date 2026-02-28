package com.ktb3.devths.chat.dto.internal;

import com.ktb3.devths.chat.dto.response.ChatMessageResponse;

public record ChatRedisMessageEnvelope(
	ChatRedisMeta meta,
	ChatRedisTraceContext trace,
	ChatMessageResponse payload
) {
}
