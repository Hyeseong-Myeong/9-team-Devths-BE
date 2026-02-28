package com.ktb3.devths.chat.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.ktb3.devths.chat.dto.request.ChatMessageRequest;
import com.ktb3.devths.chat.service.ChatMessageService;
import com.ktb3.devths.chat.tracing.ChatTraceConstants;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

	private static final String CHAT_MESSAGE_DESTINATION = "/app/chat/message";

	private final ChatMessageService chatMessageService;
	private final Tracer tracer;

	@MessageMapping("/chat/message")
	public void sendMessage(ChatMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
		Long userId = getUserIdFromSession(headerAccessor);
		String chatSessionId = getOrCreateChatSessionId(headerAccessor, request.roomId());

		Span receiveSpan = tracer.spanBuilder()
			.name("chat.message.stomp.receive")
			.kind(Span.Kind.SERVER)
			.tag("messaging.system", "stomp")
			.tag("messaging.destination", CHAT_MESSAGE_DESTINATION)
			.tag("chat.room.id", String.valueOf(request.roomId()))
			.tag("chat.user.id", String.valueOf(userId))
			.tag("chat.session.id", chatSessionId)
			.start();

		try (Tracer.SpanInScope spanInScope = tracer.withSpan(receiveSpan);
			BaggageInScope baggageInScope = tracer.createBaggageInScope(
				ChatTraceConstants.CHAT_SESSION_BAGGAGE_KEY, chatSessionId)) {
			chatMessageService.sendMessage(userId, request, chatSessionId);
		} catch (RuntimeException e) {
			receiveSpan.error(e);
			throw e;
		} finally {
			receiveSpan.end();
		}
	}

	private Long getUserIdFromSession(SimpMessageHeaderAccessor headerAccessor) {
		Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
		if (sessionAttributes == null) {
			throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
		}

		Object userId = sessionAttributes.get("userId");
		if (userId instanceof Long) {
			return (Long)userId;
		}

		throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
	}

	@SuppressWarnings("unchecked")
	private String getOrCreateChatSessionId(SimpMessageHeaderAccessor headerAccessor, Long roomId) {
		if (roomId == null) {
			return UUID.randomUUID().toString();
		}

		Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
		if (sessionAttributes == null) {
			throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
		}

		Map<Long, String> chatSessionIds = (Map<Long, String>)sessionAttributes.computeIfAbsent(
			ChatTraceConstants.CHAT_SESSION_IDS_KEY,
			key -> new ConcurrentHashMap<Long, String>()
		);

		return chatSessionIds.computeIfAbsent(roomId, key -> UUID.randomUUID().toString());
	}
}
