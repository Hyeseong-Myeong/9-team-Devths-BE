package com.ktb3.devths.chat.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chat.websocket")
public class ChatWebSocketProperties {
	private long heartbeatSendIntervalMs = 10000L;
	private long heartbeatReceiveIntervalMs = 10000L;
}
