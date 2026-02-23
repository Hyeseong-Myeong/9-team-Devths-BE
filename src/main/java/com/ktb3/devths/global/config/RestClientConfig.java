package com.ktb3.devths.global.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.ktb3.devths.global.config.properties.FastApiProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

	private final FastApiProperties fastApiProperties;
	private final RestClient.Builder restClientBuilder;  // Spring Boot 자동 설정된 Builder 주입 (trace context 전파 포함)

	@Bean
	public RestClient restClient() {
		return restClientBuilder
			.requestFactory(clientHttpRequestFactory())
			.build();
	}

	private ClientHttpRequestFactory clientHttpRequestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(fastApiProperties.getTimeout()));
		factory.setReadTimeout(Duration.ofMillis(fastApiProperties.getTimeout()));
		return factory;
	}
}
