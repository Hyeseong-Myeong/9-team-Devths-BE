package com.ktb3.devths.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.ktb3.devths.chat.config.properties.ChatWebSocketProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompChannelInterceptor stompChannelInterceptor;
	private final ChatWebSocketProperties chatWebSocketProperties;

	@Bean(name = "webSocketTaskScheduler")
	public ThreadPoolTaskScheduler webSocketTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("ws-heartbeat-");
		scheduler.initialize();
		return scheduler;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue")
			.setHeartbeatValue(new long[] {
				chatWebSocketProperties.getHeartbeatSendIntervalMs(),
				chatWebSocketProperties.getHeartbeatReceiveIntervalMs()
			})
			.setTaskScheduler(webSocketTaskScheduler());
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws/chat")
			.setAllowedOriginPatterns("*")
			.withSockJS();
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompChannelInterceptor);
	}
}
