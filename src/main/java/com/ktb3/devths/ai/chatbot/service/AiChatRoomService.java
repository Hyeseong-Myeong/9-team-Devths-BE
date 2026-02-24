package com.ktb3.devths.ai.chatbot.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.ai.chatbot.domain.constant.MessageType;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatMessage;
import com.ktb3.devths.ai.chatbot.domain.entity.AiChatRoom;
import com.ktb3.devths.ai.chatbot.dto.response.AiChatMessageListResponse;
import com.ktb3.devths.ai.chatbot.dto.response.AiChatRoomCreateResponse;
import com.ktb3.devths.ai.chatbot.dto.response.AiChatRoomListResponse;
import com.ktb3.devths.ai.chatbot.repository.AiChatMessageRepository;
import com.ktb3.devths.ai.chatbot.repository.AiChatRoomRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.notification.service.NotificationService;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiChatRoomService {

	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 100;
	private static final String DEFAULT_TITLE = "새 채팅방";

	private final AiChatRoomRepository aiChatRoomRepository;
	private final AiChatMessageRepository aiChatMessageRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;

	@Transactional
	public AiChatRoomCreateResponse createChatRoom(Long userId) {
		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		String roomUuid = java.util.UUID.randomUUID().toString();

		AiChatRoom chatRoom = AiChatRoom.builder()
			.user(user)
			.roomUuid(roomUuid)
			.title(DEFAULT_TITLE)
			.isDeleted(false)
			.build();

		AiChatRoom savedChatRoom = aiChatRoomRepository.save(chatRoom);

		return AiChatRoomCreateResponse.from(savedChatRoom);
	}

	@Transactional
	public void deleteChatRoom(Long userId, Long roomId) {
		AiChatRoom chatRoom = getOwnedRoomOrThrow(userId, roomId);

		chatRoom.delete();
	}

	@Transactional
	public AiChatMessageListResponse getChatMessages(Long userId, Long roomId, Integer size, Long lastId) {
		AiChatRoom chatRoom = getOwnedRoomOrThrow(userId, roomId);

		int pageSize = (size == null || size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		List<AiChatMessage> messages = (lastId == null)
			? aiChatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageable)
			: aiChatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, lastId, pageable);

		// 분석 완료 알림 읽음 처리
		// 조건: 첫 페이지 조회 + 메시지 1개 + REPORT 타입
		if (lastId == null && messages.size() == 1
			&& messages.getFirst().getType() == MessageType.REPORT) {
			notificationService.markReportNotificationAsReadByRoomId(roomId);
		}

		return AiChatMessageListResponse.of(messages, pageSize);
	}

	@Transactional(readOnly = true)
	public AiChatRoomListResponse getChatRoomList(Long userId, Integer size, Long lastId) {
		int pageSize = (size == null || size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

		Pageable pageable = PageRequest.of(0, pageSize + 1);

		List<AiChatRoom> chatRooms = (lastId == null)
			? aiChatRoomRepository.findByUserIdAndNotDeleted(userId, pageable)
			: aiChatRoomRepository.findByUserIdAndNotDeletedAfterCursor(userId, lastId, pageable);

		return AiChatRoomListResponse.of(chatRooms, pageSize);
	}

	@Transactional(readOnly = true)
	public AiChatRoom getOwnedRoomOrThrow(Long userId, Long roomId) {
		AiChatRoom room = aiChatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.AI_CHATROOM_NOT_FOUND));

		if (!room.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.AI_CHATROOM_ACCESS_DENIED);
		}

		return room;
	}
}
