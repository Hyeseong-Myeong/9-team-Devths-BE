package com.ktb3.devths.chat.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ktb3.devths.chat.dto.request.PrivateChatRoomCreateRequest;
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
