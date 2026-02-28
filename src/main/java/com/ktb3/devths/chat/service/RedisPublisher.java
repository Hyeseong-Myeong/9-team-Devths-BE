package com.ktb3.devths.chat.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.devths.chat.dto.internal.ChatRedisMessageEnvelope;
import com.ktb3.devths.chat.dto.internal.ChatRedisMeta;
import com.ktb3.devths.chat.dto.internal.ChatRedisNotificationEnvelope;
import com.ktb3.devths.chat.dto.internal.ChatRedisNotificationMeta;
import com.ktb3.devths.chat.dto.internal.ChatRedisTraceContext;
import com.ktb3.devths.chat.dto.response.ChatMessageResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomNotification;
import com.ktb3.devths.global.util.LogSanitizer;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPublisher {

	private static final String CHANNEL_PREFIX = "chat:room:";
	private static final String NOTIFY_PREFIX = "chat:notify:";
	private static final String MESSAGE_EVENT_TYPE = "CHAT_MESSAGE";
	private static final String NOTIFICATION_EVENT_TYPE = "CHAT_UNREAD_NOTIFICATION";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final Tracer tracer;
	private final Propagator propagator;

	public void publish(Long roomId, ChatMessageResponse response, String chatSessionId) {
		String sanitizedRoomId = LogSanitizer.sanitize(String.valueOf(roomId));

		Span publishSpan = tracer.spanBuilder()
			.name("chat.message.redis.publish")
			.kind(Span.Kind.PRODUCER)
			.tag("messaging.system", "redis")
			.tag("messaging.destination", CHANNEL_PREFIX + roomId)
			.tag("chat.room.id", sanitizedRoomId)
			.tag("chat.message.id", String.valueOf(response.messageId()))
			.tag("chat.session.id", chatSessionId)
			.start();

		try (Tracer.SpanInScope spanInScope = tracer.withSpan(publishSpan)) {
			String channel = CHANNEL_PREFIX + roomId;
			ChatRedisMessageEnvelope envelope = new ChatRedisMessageEnvelope(
				new ChatRedisMeta(MESSAGE_EVENT_TYPE, roomId, response.messageId(), chatSessionId),
				buildTraceContext(publishSpan),
				response
			);
			String message = objectMapper.writeValueAsString(envelope);
			redisTemplate.convertAndSend(channel, message);

			log.debug("Redis 메시지 발행 성공: roomId={}", sanitizedRoomId);
		} catch (Exception e) {
			publishSpan.error(e);
			log.error("Redis 메시지 발행 실패: roomId={}", sanitizedRoomId, e);
		} finally {
			publishSpan.end();
		}
	}

	public void publishNotification(Long userId, ChatRoomNotification notification, String chatSessionId) {
		String sanitizedUserId = LogSanitizer.sanitize(String.valueOf(userId));

		Span publishSpan = tracer.spanBuilder()
			.name("chat.notification.redis.publish")
			.kind(Span.Kind.PRODUCER)
			.tag("messaging.system", "redis")
			.tag("messaging.destination", NOTIFY_PREFIX + userId)
			.tag("chat.user.id", sanitizedUserId)
			.tag("chat.room.id", String.valueOf(notification.roomId()))
			.tag("chat.session.id", chatSessionId)
			.start();

		try (Tracer.SpanInScope spanInScope = tracer.withSpan(publishSpan)) {
			String channel = NOTIFY_PREFIX + userId;
			ChatRedisNotificationEnvelope envelope = new ChatRedisNotificationEnvelope(
				new ChatRedisNotificationMeta(
					NOTIFICATION_EVENT_TYPE,
					userId,
					notification.roomId(),
					chatSessionId
				),
				buildTraceContext(publishSpan),
				notification
			);
			String message = objectMapper.writeValueAsString(envelope);
			redisTemplate.convertAndSend(channel, message);

			log.debug("Redis 알림 발행 성공: userId={}", sanitizedUserId);
		} catch (Exception e) {
			publishSpan.error(e);
			log.error("Redis 알림 발행 실패: userId={}", sanitizedUserId, e);
		} finally {
			publishSpan.end();
		}
	}

	private ChatRedisTraceContext buildTraceContext(Span publishSpan) {
		Map<String, String> carrier = new HashMap<>();
		propagator.inject(publishSpan.context(), carrier, Map::put);

		return new ChatRedisTraceContext(
			carrier.get("traceparent"),
			carrier.get("tracestate"),
			carrier.get("baggage")
		);
	}
}
