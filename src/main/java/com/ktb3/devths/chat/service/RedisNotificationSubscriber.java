package com.ktb3.devths.chat.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.devths.chat.dto.internal.ChatRedisNotificationEnvelope;
import com.ktb3.devths.chat.dto.response.ChatRoomNotification;
import com.ktb3.devths.chat.tracing.ChatTraceConstants;
import com.ktb3.devths.global.util.LogSanitizer;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisNotificationSubscriber implements MessageListener {

	private static final String CHANNEL_PREFIX = "chat:notify:";
	private static final String TOPIC_PREFIX = "/topic/user/";
	private static final String TOPIC_SUFFIX = "/notifications";

	private final ObjectMapper objectMapper;
	private final SimpMessagingTemplate messagingTemplate;
	private final Tracer tracer;
	private final Propagator propagator;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String userId = "unknown";

		try {
			String channel = new String(message.getChannel());
			String body = new String(message.getBody());

			userId = channel.replace(CHANNEL_PREFIX, "");
			ChatRedisNotificationEnvelope envelope = parseEnvelope(body);
			ChatRoomNotification notification = (envelope != null && envelope.payload() != null)
				? envelope.payload()
				: objectMapper.readValue(body, ChatRoomNotification.class);
			String chatSessionId = resolveChatSessionId(envelope);

			Span consumeSpan = createConsumeSpan(envelope, userId, notification, chatSessionId);
			try (Tracer.SpanInScope consumeScope = tracer.withSpan(consumeSpan);
				BaggageInScope baggageInScope = openChatSessionBaggage(chatSessionId)) {
				sendWithFanoutSpan(userId, notification, chatSessionId);
				log.debug("Redis 알림 수신 성공: userId={}", LogSanitizer.sanitize(userId));
			} catch (RuntimeException e) {
				consumeSpan.error(e);
				log.error("Redis 알림 수신 실패: userId={}", LogSanitizer.sanitize(userId), e);
			} finally {
				consumeSpan.end();
			}
		} catch (Exception e) {
			log.error("Redis 알림 수신 실패: userId={}", LogSanitizer.sanitize(userId), e);
		}
	}

	private void sendWithFanoutSpan(String userId, ChatRoomNotification notification, String chatSessionId) {
		Span fanoutSpan = tracer.spanBuilder()
			.name("chat.notification.stomp.fanout")
			.kind(Span.Kind.PRODUCER)
			.tag("messaging.system", "stomp")
			.tag("messaging.destination", TOPIC_PREFIX + userId + TOPIC_SUFFIX)
			.tag("chat.user.id", LogSanitizer.sanitize(userId))
			.tag("chat.room.id", String.valueOf(notification.roomId()))
			.tag("chat.session.id", chatSessionId)
			.start();

		try (Tracer.SpanInScope fanoutScope = tracer.withSpan(fanoutSpan)) {
			messagingTemplate.convertAndSend(TOPIC_PREFIX + userId + TOPIC_SUFFIX, notification);
		} catch (RuntimeException e) {
			fanoutSpan.error(e);
			throw e;
		} finally {
			fanoutSpan.end();
		}
	}

	private Span createConsumeSpan(
		ChatRedisNotificationEnvelope envelope,
		String userId,
		ChatRoomNotification notification,
		String chatSessionId
	) {
		return extractParentSpanBuilder(envelope)
			.name("chat.notification.redis.consume")
			.kind(Span.Kind.CONSUMER)
			.tag("messaging.system", "redis")
			.tag("messaging.destination", CHANNEL_PREFIX + userId)
			.tag("chat.user.id", LogSanitizer.sanitize(userId))
			.tag("chat.room.id", String.valueOf(notification.roomId()))
			.tag("chat.session.id", chatSessionId)
			.start();
	}

	private Span.Builder extractParentSpanBuilder(ChatRedisNotificationEnvelope envelope) {
		if (envelope == null || envelope.trace() == null || envelope.trace().traceparent() == null) {
			return tracer.spanBuilder();
		}

		Map<String, String> carrier = new HashMap<>();
		carrier.put("traceparent", envelope.trace().traceparent());
		putIfNotBlank(carrier, "tracestate", envelope.trace().tracestate());
		putIfNotBlank(carrier, "baggage", envelope.trace().baggage());
		return propagator.extract(carrier, Map::get);
	}

	private BaggageInScope openChatSessionBaggage(String chatSessionId) {
		if (chatSessionId == null || chatSessionId.isBlank()) {
			return BaggageInScope.NOOP;
		}
		return tracer.createBaggageInScope(ChatTraceConstants.CHAT_SESSION_BAGGAGE_KEY, chatSessionId);
	}

	private ChatRedisNotificationEnvelope parseEnvelope(String body) {
		try {
			ChatRedisNotificationEnvelope envelope = objectMapper.readValue(body, ChatRedisNotificationEnvelope.class);
			return envelope != null && envelope.payload() != null ? envelope : null;
		} catch (Exception e) {
			return null;
		}
	}

	private String resolveChatSessionId(ChatRedisNotificationEnvelope envelope) {
		if (envelope == null || envelope.meta() == null || envelope.meta().chatSessionId() == null) {
			return "unknown";
		}
		return envelope.meta().chatSessionId();
	}

	private void putIfNotBlank(Map<String, String> carrier, String key, String value) {
		if (value != null && !value.isBlank()) {
			carrier.put(key, value);
		}
	}
}
