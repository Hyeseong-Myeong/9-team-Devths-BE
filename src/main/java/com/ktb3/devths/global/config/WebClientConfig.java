package com.ktb3.devths.global.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.ktb3.devths.global.config.properties.FastApiProperties;

import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

	private final FastApiProperties fastApiProperties;
	private final WebClient.Builder webClientBuilder;  // Spring Boot 자동 설정된 Builder 주입 (trace context 전파 포함)

	@Bean
	public WebClient webClient() {
		HttpClient httpClient = HttpClient.create()
			.responseTimeout(Duration.ofSeconds(60));

		return webClientBuilder
			.baseUrl(fastApiProperties.getBaseUrl())
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.codecs(configurer ->
				configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)
			)
			.build();
	}
}
