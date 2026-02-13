package com.ktb3.devths.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.ktb3.devths.chat.service.RedisNotificationSubscriber;
import com.ktb3.devths.chat.service.RedisSubscriber;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

	private final RedisSubscriber redisSubscriber;
	private final RedisNotificationSubscriber redisNotificationSubscriber;

	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(redisSubscriber, new PatternTopic("chat:room:*"));
		container.addMessageListener(redisNotificationSubscriber, new PatternTopic("chat:notify:*"));
		return container;
	}
}
