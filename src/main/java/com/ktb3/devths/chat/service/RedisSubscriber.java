package com.ktb3.devths.chat.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.devths.chat.dto.internal.ChatRedisMessageEnvelope;
import com.ktb3.devths.chat.dto.response.ChatMessageResponse;
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
public class RedisSubscriber implements MessageListener {

	private static final String CHANNEL_PREFIX = "chat:room:";
	private static final String TOPIC_PREFIX = "/topic/chatroom/";

	private final ObjectMapper objectMapper;
	private final SimpMessagingTemplate messagingTemplate;
	private final Tracer tracer;
	private final Propagator propagator;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String roomId = "unknown";

		try {
			String channel = new String(message.getChannel());
			String body = new String(message.getBody());

			roomId = channel.replace(CHANNEL_PREFIX, "");
			ChatRedisMessageEnvelope envelope = parseEnvelope(body);
			ChatMessageResponse response = (envelope != null && envelope.payload() != null)
				? envelope.payload()
				: objectMapper.readValue(body, ChatMessageResponse.class);
			String chatSessionId = resolveChatSessionId(envelope);

			Span consumeSpan = createConsumeSpan(envelope, roomId, chatSessionId);
			try (Tracer.SpanInScope consumeScope = tracer.withSpan(consumeSpan);
				BaggageInScope baggageInScope = openChatSessionBaggage(chatSessionId)) {
				sendWithFanoutSpan(roomId, response, chatSessionId);

				log.debug("Redis 메시지 수신 성공: roomId={}", LogSanitizer.sanitize(roomId));
			} catch (RuntimeException e) {
				consumeSpan.error(e);
				log.error("Redis 메시지 수신 실패: roomId={}", LogSanitizer.sanitize(roomId), e);
			} finally {
				consumeSpan.end();
			}
		} catch (Exception e) {
			log.error("Redis 메시지 수신 실패: roomId={}", LogSanitizer.sanitize(roomId), e);
		}
	}

	private void sendWithFanoutSpan(String roomId, ChatMessageResponse response, String chatSessionId) {
		Span fanoutSpan = tracer.spanBuilder()
			.name("chat.message.stomp.fanout")
			.kind(Span.Kind.PRODUCER)
			.tag("messaging.system", "stomp")
			.tag("messaging.destination", TOPIC_PREFIX + roomId)
			.tag("chat.room.id", LogSanitizer.sanitize(roomId))
			.tag("chat.message.id", String.valueOf(response.messageId()))
			.tag("chat.session.id", chatSessionId)
			.start();

		try (Tracer.SpanInScope fanoutScope = tracer.withSpan(fanoutSpan)) {
			messagingTemplate.convertAndSend(TOPIC_PREFIX + roomId, response);
		} catch (RuntimeException e) {
			fanoutSpan.error(e);
			throw e;
		} finally {
			fanoutSpan.end();
		}
	}

	private Span createConsumeSpan(ChatRedisMessageEnvelope envelope, String roomId, String chatSessionId) {
		Span.Builder spanBuilder = extractParentSpanBuilder(envelope)
			.name("chat.message.redis.consume")
			.kind(Span.Kind.CONSUMER)
			.tag("messaging.system", "redis")
			.tag("messaging.destination", CHANNEL_PREFIX + roomId)
			.tag("chat.room.id", LogSanitizer.sanitize(roomId))
			.tag("chat.session.id", chatSessionId);

		ChatMessageResponse payload = envelope != null ? envelope.payload() : null;
		if (payload != null && payload.messageId() != null) {
			spanBuilder.tag("chat.message.id", String.valueOf(payload.messageId()));
		}

		return spanBuilder.start();
	}

	private Span.Builder extractParentSpanBuilder(ChatRedisMessageEnvelope envelope) {
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

	private ChatRedisMessageEnvelope parseEnvelope(String body) {
		try {
			ChatRedisMessageEnvelope envelope = objectMapper.readValue(body, ChatRedisMessageEnvelope.class);
			return envelope != null && envelope.payload() != null ? envelope : null;
		} catch (Exception e) {
			return null;
		}
	}

	private String resolveChatSessionId(ChatRedisMessageEnvelope envelope) {
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
