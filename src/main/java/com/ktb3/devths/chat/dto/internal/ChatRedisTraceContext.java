package com.ktb3.devths.chat.dto.internal;

public record ChatRedisTraceContext(
	String traceparent,
	String tracestate,
	String baggage
) {
}
