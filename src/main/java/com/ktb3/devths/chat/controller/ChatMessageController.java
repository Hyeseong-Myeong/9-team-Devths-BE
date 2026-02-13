package com.ktb3.devths.chat.controller;

import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.ktb3.devths.chat.dto.request.ChatMessageRequest;
import com.ktb3.devths.chat.service.ChatMessageService;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

	private final ChatMessageService chatMessageService;

	@MessageMapping("/chat/message")
	public void sendMessage(ChatMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
		Long userId = getUserIdFromSession(headerAccessor);

		chatMessageService.sendMessage(userId, request);
	}

	private Long getUserIdFromSession(SimpMessageHeaderAccessor headerAccessor) {
		Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
		if (sessionAttributes == null) {
			throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
		}

		Object userId = sessionAttributes.get("userId");
		if (userId instanceof Long) {
			return (Long)userId;
		}

		throw new CustomException(ErrorCode.WEBSOCKET_AUTH_FAILED);
	}
}
