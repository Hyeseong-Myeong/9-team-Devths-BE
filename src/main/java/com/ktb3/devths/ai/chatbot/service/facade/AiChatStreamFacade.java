package com.ktb3.devths.ai.chatbot.service.facade;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.ktb3.devths.ai.chatbot.dto.request.AiChatMessageRequest;
import com.ktb3.devths.ai.chatbot.dto.request.InterviewEvaluationRequest;
import com.ktb3.devths.ai.chatbot.service.AiChatInterviewService;
import com.ktb3.devths.ai.chatbot.service.AiChatMessageService;
import com.ktb3.devths.ai.chatbot.service.AiChatRoomService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiChatStreamFacade {

	private static final String ERROR_PREFIX = "ERROR:";

	private final AiChatRoomService aiChatRoomService;
	private final AiChatMessageService aiChatMessageService;
	private final AiChatInterviewService aiChatInterviewService;

	public Flux<ServerSentEvent<String>> sendMessageStream(Long userId, Long roomId, AiChatMessageRequest request) {
		aiChatRoomService.getOwnedRoomOrThrow(userId, roomId);

		Flux<String> chatStream = aiChatMessageService.streamChatResponse(
			userId,
			roomId,
			request.content(),
			request.model(),
			request.interviewId()
		);
		return toSseStream(chatStream);
	}

	public Flux<ServerSentEvent<String>> evaluateInterviewStream(
		Long userId,
		Long roomId,
		InterviewEvaluationRequest request
	) {
		aiChatRoomService.getOwnedRoomOrThrow(userId, roomId);

		boolean retry = request.retry() != null && request.retry();
		Flux<String> evaluationStream = aiChatInterviewService.evaluateInterview(request.interviewId(), retry);
		return toSseStream(evaluationStream);
	}

	private Flux<ServerSentEvent<String>> toSseStream(Flux<String> source) {
		return source
			.map(chunk -> chunk.startsWith(ERROR_PREFIX)
				? buildErrorEvent(chunk.substring(ERROR_PREFIX.length()))
				: buildDataEvent(chunk))
			.concatWith(Mono.just(buildDoneEvent()));
	}

	private ServerSentEvent<String> buildErrorEvent(String errorMsg) {
		return ServerSentEvent.<String>builder()
			.event("error")
			.data("{\"message\": \"" + errorMsg + "\"}")
			.build();
	}

	private ServerSentEvent<String> buildDataEvent(String chunk) {
		return ServerSentEvent.<String>builder()
			.data(chunk)
			.build();
	}

	private ServerSentEvent<String> buildDoneEvent() {
		return ServerSentEvent.<String>builder()
			.event("done")
			.data("")
			.build();
	}
}
