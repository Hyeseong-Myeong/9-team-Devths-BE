package com.ktb3.devths.chat.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ktb3.devths.chat.dto.request.ChatRoomUpdateRequest;
import com.ktb3.devths.chat.dto.request.PrivateChatRoomCreateRequest;
import com.ktb3.devths.chat.dto.response.ChatRoomDetailResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomListResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomUpdateResponse;
import com.ktb3.devths.chat.dto.response.PrivateChatRoomCreateResponse;
import com.ktb3.devths.chat.service.ChatRoomService;
import com.ktb3.devths.global.response.ApiResponse;
import com.ktb3.devths.global.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chatrooms")
@RequiredArgsConstructor
public class ChatRoomController {

	private final ChatRoomService chatRoomService;

	@GetMapping
	public ResponseEntity<ApiResponse<ChatRoomListResponse>> getChatRoomList(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@RequestParam(defaultValue = "PRIVATE") String type,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) LocalDateTime cursor
	) {
		ChatRoomListResponse response = chatRoomService.getChatRoomList(
			userPrincipal.getUserId(),
			type,
			size,
			cursor
		);

		return ResponseEntity.ok(ApiResponse.success("채팅방 목록을 성공적으로 조회하였습니다.", response));
	}

	@GetMapping("/{roomId}")
	public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> getChatRoomDetail(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId
	) {
		ChatRoomDetailResponse response = chatRoomService.getChatRoomDetail(
			userPrincipal.getUserId(),
			roomId
		);

		return ResponseEntity.ok(ApiResponse.success("채팅방 상세 정보를 성공적으로 조회하였습니다.", response));
	}

	@PutMapping("/{roomId}")
	public ResponseEntity<ApiResponse<ChatRoomUpdateResponse>> updateChatRoom(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId,
		@Valid @RequestBody ChatRoomUpdateRequest request
	) {
		ChatRoomUpdateResponse response = chatRoomService.updateChatRoom(
			userPrincipal.getUserId(),
			roomId,
			request
		);

		return ResponseEntity.ok(ApiResponse.success("채팅방 정보가 성공적으로 수정되었습니다.", response));
	}

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204")
	@DeleteMapping("/{roomId}")
	public ResponseEntity<Void> leaveChatRoom(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long roomId
	) {
		chatRoomService.leaveChatRoom(
			userPrincipal.getUserId(),
			roomId
		);

		return ResponseEntity.noContent().build();
	}

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201")
	@PostMapping("/private")
	public ResponseEntity<ApiResponse<PrivateChatRoomCreateResponse>> createPrivateChatRoom(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@Valid @RequestBody PrivateChatRoomCreateRequest request
	) {
		PrivateChatRoomCreateResponse response = chatRoomService.createPrivateChatRoom(
			userPrincipal.getUserId(),
			request.userId()
		);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success("채팅방이 성공적으로 생성되었습니다.", response));
	}
}
