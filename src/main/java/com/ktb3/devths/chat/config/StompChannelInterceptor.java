package com.ktb3.devths.chat.config;

import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.ktb3.devths.chat.repository.ChatMemberRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.security.jwt.JwtTokenProvider;
import com.ktb3.devths.global.security.jwt.JwtTokenValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String CHATROOM_TOPIC_PREFIX = "/topic/chatroom/";
	private static final String USER_TOPIC_PREFIX = "/topic/user/";
	private static final String USER_TOPIC_SUFFIX = "/notifications";

	private final JwtTokenValidator jwtTokenValidator;
	private final JwtTokenProvider jwtTokenProvider;
	private final ChatMemberRepository chatMemberRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		switch (accessor.getCommand()) {
			case CONNECT -> handleConnect(accessor);
			case SUBSCRIBE -> handleSubscribe(accessor);
			default -> {
			}
		}

		return message;
	}

	private void handleConnect(StompHeaderAccessor accessor) {
		String authHeader = accessor.getFirstNativeHeader("Authorization");

		if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
			log.warn("WebSocket CONNECT: Authorization 헤더 없음");
			throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
		}

		String token = authHeader.substring(BEARER_PREFIX.length());

		try {
			jwtTokenValidator.validateAccessToken(token);
			Long userId = jwtTokenProvider.getUserIdFromToken(token);

			Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
			if (sessionAttributes != null) {
				sessionAttributes.put("userId", userId);
			}

			log.info("WebSocket 인증 성공: userId={}", userId);
		} catch (CustomException e) {
			log.warn("WebSocket 인증 실패: {}", e.getMessage());
			throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
		}
	}

	private void handleSubscribe(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();

		if (destination == null) {
			return;
		}

		if (destination.startsWith(CHATROOM_TOPIC_PREFIX)) {
			handleChatroomSubscribe(accessor, destination);
		} else if (destination.startsWith(USER_TOPIC_PREFIX) && destination.endsWith(USER_TOPIC_SUFFIX)) {
			handleUserNotificationSubscribe(accessor, destination);
		}
	}

	private void handleChatroomSubscribe(StompHeaderAccessor accessor, String destination) {
		Long userId = getUserIdFromSession(accessor);
		if (userId == null) {
			log.warn("WebSocket SUBSCRIBE: 세션에 userId 없음");
			throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
		}

		String roomIdStr = destination.replace(CHATROOM_TOPIC_PREFIX, "");
		Long roomId;
		try {
			roomId = Long.parseLong(roomIdStr);
		} catch (NumberFormatException e) {
			log.warn("WebSocket SUBSCRIBE: 잘못된 roomId={}", roomIdStr);
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		chatMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
			.orElseThrow(() -> {
				log.warn("WebSocket SUBSCRIBE: 채팅방 멤버 아님 - roomId={}, userId={}", roomId, userId);
				return new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED);
			});

		log.info("WebSocket 구독 인증 성공: roomId={}, userId={}", roomId, userId);
	}

	private void handleUserNotificationSubscribe(StompHeaderAccessor accessor, String destination) {
		Long sessionUserId = getUserIdFromSession(accessor);
		if (sessionUserId == null) {
			log.warn("WebSocket SUBSCRIBE: 세션에 userId 없음");
			throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
		}

		String pathUserId = destination.replace(USER_TOPIC_PREFIX, "").replace(USER_TOPIC_SUFFIX, "");
		Long targetUserId;
		try {
			targetUserId = Long.parseLong(pathUserId);
		} catch (NumberFormatException e) {
			log.warn("WebSocket SUBSCRIBE: 잘못된 userId={}", pathUserId);
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		if (!sessionUserId.equals(targetUserId)) {
			log.warn("WebSocket SUBSCRIBE: 타인의 알림 채널 구독 시도 - sessionUserId={}, targetUserId={}",
				sessionUserId, targetUserId);
			throw new CustomException(ErrorCode.ACCESS_DENIED);
		}

		log.info("WebSocket 알림 구독 성공: userId={}", sessionUserId);
	}

	private Long getUserIdFromSession(StompHeaderAccessor accessor) {
		Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
		if (sessionAttributes == null) {
			return null;
		}

		Object userId = sessionAttributes.get("userId");
		if (userId instanceof Long) {
			return (Long)userId;
		}

		return null;
	}
}
