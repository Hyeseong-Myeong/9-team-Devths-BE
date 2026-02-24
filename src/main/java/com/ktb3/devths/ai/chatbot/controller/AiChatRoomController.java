package com.ktb3.devths.ai.chatbot.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ktb3.devths.ai.chatbot.dto.request.AiChatMessageRequest;
import com.ktb3.devths.ai.chatbot.dto.request.InterviewEndRequest;
import com.ktb3.devths.ai.chatbot.dto.request.InterviewEvaluationRequest;
import com.ktb3.devths.ai.chatbot.dto.request.InterviewStartRequest;
import com.ktb3.devths.ai.chatbot.dto.response.AiChatMessageListResponse;
import com.ktb3.devths.ai.chatbot.dto.response.AiChatRoomCreateResponse;
import com.ktb3.devths.ai.chatbot.dto.response.AiChatRoomListResponse;
import com.ktb3.devths.ai.chatbot.dto.response.CurrentInterviewResponse;
import com.ktb3.devths.ai.chatbot.dto.response.InterviewEndResponse;
import com.ktb3.devths.ai.chatbot.dto.response.InterviewStartResponse;
import com.ktb3.devths.ai.chatbot.service.AiChatRoomService;
import com.ktb3.devths.ai.chatbot.service.facade.AiChatStreamFacade;
import com.ktb3.devths.ai.chatbot.service.facade.AiInterviewFacade;
import com.ktb3.devths.global.response.ApiResponse;
import com.ktb3.devths.global.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai-chatrooms")
@RequiredArgsConstructor
public class AiChatRoomController {

	private final AiChatRoomService aiChatRoomService;
	private final AiInterviewFacade aiInterviewFacade;
	private final AiChatStreamFacade aiChatStreamFacade;

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201")
	@PostMapping
	public ResponseEntity<ApiResponse<AiChatRoomCreateResponse>> createChatRoom(
		@AuthenticationPrincipal UserPrincipal userPrincipal
	) {
		AiChatRoomCreateResponse response = aiChatRoomService.createChatRoom(userPrincipal.getUserId());

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success("AI 채팅방이 성공적으로 생성되었습니다.", response));
	}

	@GetMapping("/{roomId}/messages")
	public ResponseEntity<ApiResponse<AiChatMessageListResponse>> getChatMessages(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) Long lastId
	) {
		AiChatMessageListResponse response = aiChatRoomService.getChatMessages(
			userPrincipal.getUserId(),
			roomId,
			size,
			lastId
		);

		return ResponseEntity.ok(ApiResponse.success("AI 채팅 내역을 성공적으로 조회하였습니다.", response));
	}

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204")
	@DeleteMapping("/{roomId}")
	public ResponseEntity<Void> deleteChatRoom(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId
	) {
		aiChatRoomService.deleteChatRoom(userPrincipal.getUserId(), roomId);

		return ResponseEntity.noContent().build();
	}

	@GetMapping
	public ResponseEntity<ApiResponse<AiChatRoomListResponse>> getChatRoomList(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) Long lastId
	) {
		AiChatRoomListResponse response = aiChatRoomService.getChatRoomList(
			userPrincipal.getUserId(),
			size,
			lastId
		);

		return ResponseEntity.ok(
			ApiResponse.success("AI 채팅방 목록을 성공적으로 조회하였습니다.", response)
		);
	}

	@PostMapping(value = "/{roomId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> sendMessage(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId,
		@Valid @RequestBody AiChatMessageRequest request
	) {
		return aiChatStreamFacade.sendMessageStream(userPrincipal.getUserId(), roomId, request);
	}

	@GetMapping("/{roomId}/interview/current")
	public ResponseEntity<ApiResponse<CurrentInterviewResponse>> getCurrentInterview(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId
	) {
		CurrentInterviewResponse response = aiInterviewFacade.getCurrentInterview(userPrincipal.getUserId(), roomId);
		if (response == null) {
			return ResponseEntity.ok(
				ApiResponse.success("진행 중인 면접이 없습니다.", null)
			);
		}
		return ResponseEntity.ok(
			ApiResponse.success("진행 중인 면접 정보를 조회했습니다.", response)
		);
	}

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201")
	@PostMapping("/{roomId}/interview")
	public ResponseEntity<ApiResponse<InterviewStartResponse>> startInterview(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId,
		@Valid @RequestBody InterviewStartRequest request
	) {
		AiInterviewFacade.InterviewStartFacadeResult result = aiInterviewFacade.startInterview(
			userPrincipal.getUserId(),
			roomId,
			request
		);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(result.message(), result.response()));
	}

	@PostMapping("/{roomId}/interview/end")
	public ResponseEntity<ApiResponse<InterviewEndResponse>> endInterview(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId,
		@Valid @RequestBody InterviewEndRequest request
	) {
		InterviewEndResponse response = aiInterviewFacade.endInterview(userPrincipal.getUserId(), roomId, request);

		return ResponseEntity.ok(
			ApiResponse.success("면접이 종료되었습니다.", response)
		);
	}

	@PostMapping(value = "/{roomId}/evaluation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> evaluateInterview(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId,
		@Valid @RequestBody InterviewEvaluationRequest request
	) {
		return aiChatStreamFacade.evaluateInterviewStream(userPrincipal.getUserId(), roomId, request);
	}
}
