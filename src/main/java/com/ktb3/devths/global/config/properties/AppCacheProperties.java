package com.ktb3.devths.global.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cache")
public class AppCacheProperties {

	private boolean enabled = false;
	private PostDetail postDetail = new PostDetail();

	@Getter
	@Setter
	public static class PostDetail {
		private boolean enabled = false;
		private Duration ttl = Duration.ofSeconds(60);
	}
}
