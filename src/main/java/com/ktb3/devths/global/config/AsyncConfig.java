package com.ktb3.devths.global.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.Getter;
import lombok.Setter;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean
	@ConfigurationProperties(prefix = "async")
	public AsyncProperties asyncProperties() {
		return new AsyncProperties();
	}

	@Bean(name = "taskExecutor")
	public Executor taskExecutor(AsyncProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.getCorePoolSize());
		executor.setMaxPoolSize(properties.getMaxPoolSize());
		executor.setQueueCapacity(properties.getQueueCapacity());
		executor.setThreadNamePrefix(properties.getThreadNamePrefix());
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		executor.initialize();
		return executor;
	}

	@Getter
	@Setter
	public static class AsyncProperties {
		private int corePoolSize = 5;
		private int maxPoolSize = 10;
		private int queueCapacity = 100;
		private String threadNamePrefix = "async-";
	}
}
