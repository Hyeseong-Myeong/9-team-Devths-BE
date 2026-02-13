package com.ktb3.devths.chat.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.devths.chat.dto.response.ChatMessageResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomNotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPublisher {

	private static final String CHANNEL_PREFIX = "chat:room:";
	private static final String NOTIFY_PREFIX = "chat:notify:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public void publish(Long roomId, ChatMessageResponse response) {
		try {
			String channel = CHANNEL_PREFIX + roomId;
			String message = objectMapper.writeValueAsString(response);
			redisTemplate.convertAndSend(channel, message);
			log.debug("Redis 메시지 발행: channel={}", channel);
		} catch (Exception e) {
			log.error("Redis 메시지 발행 실패: roomId={}", roomId, e);
		}
	}

	public void publishNotification(Long userId, ChatRoomNotification notification) {
		try {
			String channel = NOTIFY_PREFIX + userId;
			String message = objectMapper.writeValueAsString(notification);
			redisTemplate.convertAndSend(channel, message);
			log.debug("Redis 알림 발행: channel={}", channel);
		} catch (Exception e) {
			log.error("Redis 알림 발행 실패: userId={}", userId, e);
		}
	}
}
