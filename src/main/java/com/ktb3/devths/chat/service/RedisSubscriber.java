package com.ktb3.devths.chat.service;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.devths.chat.dto.response.ChatMessageResponse;

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

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			String channel = new String(message.getChannel());
			String body = new String(message.getBody());

			String roomId = channel.replace(CHANNEL_PREFIX, "");

			ChatMessageResponse response = objectMapper.readValue(body, ChatMessageResponse.class);

			messagingTemplate.convertAndSend(TOPIC_PREFIX + roomId, response);

			log.debug("Redis 메시지 수신 → STOMP 전달: roomId={}", roomId);
		} catch (Exception e) {
			log.error("Redis 메시지 처리 실패", e);
		}
	}
}
