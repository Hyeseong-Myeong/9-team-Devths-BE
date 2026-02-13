package com.ktb3.devths.chat.service;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.devths.chat.dto.response.ChatRoomNotification;

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

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			String channel = new String(message.getChannel());
			String body = new String(message.getBody());

			String userId = channel.replace(CHANNEL_PREFIX, "");

			ChatRoomNotification notification = objectMapper.readValue(body, ChatRoomNotification.class);

			messagingTemplate.convertAndSend(TOPIC_PREFIX + userId + TOPIC_SUFFIX, notification);

			log.debug("Redis 알림 수신 → STOMP 전달: userId={}, roomId={}", userId, notification.roomId());
		} catch (Exception e) {
			log.error("Redis 알림 처리 실패", e);
		}
	}
}
