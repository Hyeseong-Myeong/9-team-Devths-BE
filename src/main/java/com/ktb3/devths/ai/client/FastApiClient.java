package com.ktb3.devths.ai.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.devths.ai.analysis.dto.request.FastApiAnalysisRequest;
import com.ktb3.devths.ai.analysis.dto.response.FastApiAnalysisResponse;
import com.ktb3.devths.ai.analysis.dto.response.FastApiTaskStatusResponse;
import com.ktb3.devths.ai.chatbot.dto.request.FastApiChatRequest;
import com.ktb3.devths.ai.chatbot.dto.request.FastApiInterviewEvaluationRequest;
import com.ktb3.devths.global.config.properties.FastApiProperties;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.ratelimit.domain.constant.ApiType;
import com.ktb3.devths.global.ratelimit.service.RateLimitService;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.util.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class FastApiClient {

	private final RestClient restClient;
	private final WebClient webClient;
	private final FastApiProperties fastApiProperties;
	private final ObjectMapper objectMapper;
	private final RateLimitService rateLimitService;

	public FastApiAnalysisResponse requestAnalysis(FastApiAnalysisRequest request) {
		rateLimitService.consumeToken(request.userId(), ApiType.FASTAPI_ANALYSIS);

		try {
			String url = fastApiProperties.getBaseUrl() + "/ai/text/extract";

			log.info("FastAPI 분석 요청 전송: taskId={}, roomId={}, userId={}",
				request.taskId(), request.roomId(), request.userId());

			FastApiAnalysisResponse response = restClient.post()
				.uri(url)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(FastApiAnalysisResponse.class);

			if (response == null) {
				throw new CustomException(ErrorCode.FASTAPI_CONNECTION_FAILED);
			}

			log.info("FastAPI 분석 요청 성공 - 요청 taskId={}, 응답 taskId={}",
				request.taskId(), response.taskId());
			return response;

		} catch (RestClientException e) {
			log.error("FastAPI 분석 요청 실패", e);
			throw new CustomException(ErrorCode.FASTAPI_CONNECTION_FAILED);
		}
	}

	public FastApiTaskStatusResponse pollTaskStatus(Long taskId) {
		try {
			String url = fastApiProperties.getBaseUrl() + "/ai/task/" + taskId;

			FastApiTaskStatusResponse response = restClient.get()
				.uri(url)
				.retrieve()
				.body(FastApiTaskStatusResponse.class);

			if (response == null) {
				throw new CustomException(ErrorCode.FASTAPI_CONNECTION_FAILED);
			}

			return response;

		} catch (RestClientException e) {
			log.error("FastAPI 작업 상태 조회 실패: taskId={}", taskId, e);
			throw new CustomException(ErrorCode.FASTAPI_CONNECTION_FAILED);
		}
	}

	public Flux<String> streamChatResponse(FastApiChatRequest request) {
		rateLimitService.consumeToken(request.userId(), ApiType.FASTAPI_ANALYSIS);

		String url = fastApiProperties.getBaseUrl() + "/ai/chat";
		return webClient.post()
			.uri(url)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(request)
			.retrieve()
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
			})
			.mapNotNull(sse -> {
				String data = sse.data();
				if (data == null) {
					return null;
				}
				log.debug("SSE 이벤트 수신 - id: {}, event: {}, data: '{}'", sse.id(), sse.event(), data);
				return parseChunk(data);
			})
			.filter(chunk -> !chunk.equals("[DONE]"))
			.doOnError(e -> log.error("FastAPI 스트리밍 실패", e))
			.onErrorResume(e -> {
				log.error("FastAPI 스트리밍 에러", e);
				return Flux.error(new CustomException(ErrorCode.FASTAPI_CONNECTION_FAILED));
			});
	}

	public Flux<String> streamInterviewEvaluation(FastApiInterviewEvaluationRequest request) {
		rateLimitService.consumeToken(request.value().userId(), ApiType.FASTAPI_ANALYSIS);

		String url = fastApiProperties.getBaseUrl() + "/ai/evaluation/analyze";

		// 메타데이터만 로깅 (민감 정보 제외)
		log.info("면접 평가 요청 - sessionId={}, type={}, contextCount={}",
			LogSanitizer.sanitize(String.valueOf(request.value().sessionId())),
			request.value().interviewType(),
			request.value().context().size());

		return webClient.post()
			.uri(url)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(request)
			.retrieve()
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
			})
			.mapNotNull(sse -> {
				String data = sse.data();
				if (data == null) {
					return null;
				}
				log.debug("SSE 이벤트 수신 (면접 평가) - id: {}, event: {}, data: '{}'", sse.id(), sse.event(), data);
				return parseChunk(data);
			})
			.filter(chunk -> !chunk.equals("[DONE]"))
			.doOnError(e -> {
				log.error("FastAPI 면접 평가 스트리밍 실패", e);

				// WebClientResponseException인 경우 에러 응답 body 로깅 (디버깅 필수)
				if (e instanceof WebClientResponseException) {
					WebClientResponseException webEx =
						(WebClientResponseException)e;
					log.error("FastAPI 에러 응답 - status={}, body={}",
						webEx.getStatusCode(),
						LogSanitizer.sanitize(webEx.getResponseBodyAsString()));
				}
			})
			.onErrorResume(e -> {
				log.error("FastAPI 면접 평가 스트리밍 에러", e);
				return Flux.error(new CustomException(ErrorCode.FASTAPI_CONNECTION_FAILED));
			});
	}

	private String parseChunk(String data) {
		try {
			// ServerSentEvent에서 이미 data 부분만 추출되어 옴
			log.debug("파싱할 데이터: '{}'", data);

			// [DONE] 신호 체크
			if (data.equals("[DONE]")) {
				log.debug("스트리밍 종료 신호 수신");
				return "[DONE]";
			}

			// JSON 파싱
			JsonNode node = objectMapper.readTree(data);
			log.debug("파싱된 JSON: {}", node);

			// 에러 페이로드 감지
			if (node.has("type") && "error".equals(node.get("type").asText())) {
				String fallbackMessage = node.path("fallback").asText("");
				String errorCode = node.path("error").path("code").asText("");
				int errorStatus = node.path("error").path("status").asInt(0);

				log.warn("FastAPI 에러 응답 수신 - code: {}, status: {}, fallback: '{}'",
					errorCode, errorStatus, fallbackMessage);

				// 특수 마커 반환: "[ERROR]" + fallback 메시지
				return "[ERROR]" + fallbackMessage;
			}

			// 정상 청크
			if (node.has("chunk")) {
				String chunk = node.get("chunk").asText();
				log.debug("추출된 chunk (길이: {}): '{}'", chunk.length(),
					chunk.length() > 50 ? chunk.substring(0, 50) + "..." : chunk);
				return chunk;
			}

			log.warn("JSON에 'chunk' 필드가 없음: {}", node);
			return "";
		} catch (Exception e) {
			log.error("청크 파싱 실패: data={}", LogSanitizer.sanitize(data), e);
			return "";
		}
	}
}
