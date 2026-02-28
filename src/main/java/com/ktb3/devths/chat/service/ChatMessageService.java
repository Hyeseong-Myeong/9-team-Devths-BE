package com.ktb3.devths.chat.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.chat.domain.constant.ChatMessageTypes;
import com.ktb3.devths.chat.domain.constant.ChatRoomTypes;
import com.ktb3.devths.chat.domain.entity.ChatMember;
import com.ktb3.devths.chat.domain.entity.ChatMessage;
import com.ktb3.devths.chat.domain.entity.ChatRoom;
import com.ktb3.devths.chat.dto.request.ChatMessageRequest;
import com.ktb3.devths.chat.dto.response.ChatMessageListResponse;
import com.ktb3.devths.chat.dto.response.ChatMessageResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomNotification;
import com.ktb3.devths.chat.repository.ChatMemberRepository;
import com.ktb3.devths.chat.repository.ChatMessageRepository;
import com.ktb3.devths.chat.repository.ChatPrivateRoomRepository;
import com.ktb3.devths.chat.repository.ChatRoomRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.storage.domain.constant.RefType;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.repository.S3AttachmentRepository;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.global.util.LogSanitizer;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatPrivateRoomRepository chatPrivateRoomRepository;
	private final ChatMemberRepository chatMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserRepository userRepository;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final S3StorageService s3StorageService;
	private final RedisPublisher redisPublisher;

	@Transactional
	public ChatMessageListResponse getChatMessages(Long userId, Long roomId, Integer size, Long lastId) {
		chatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		int pageSize = (size == null || size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		LocalDateTime joinedAt = member.getJoinedAt();

		List<ChatMessage> messages = (lastId == null)
			? chatMessageRepository.findByChatRoomIdAndCreatedAtAfterOrderByIdDesc(roomId, joinedAt, pageable)
			: chatMessageRepository.findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(roomId, lastId,
			joinedAt, pageable);

		boolean hasNext = messages.size() > pageSize;
		List<ChatMessage> actualMessages = hasNext
			? messages.subList(0, pageSize)
			: messages;

		List<ChatMessage> ordered = new ArrayList<>(actualMessages);
		Collections.reverse(ordered);

		Map<Long, String> profileImageMap = fetchProfileImages(ordered);

		List<ChatMessageResponse> responses = ordered.stream()
			.map(msg -> buildHistoryResponse(msg, profileImageMap))
			.toList();

		Long nextCursor = responses.isEmpty() ? null : responses.getFirst().messageId();

		if (lastId == null && !ordered.isEmpty()) {
			Long latestMsgId = ordered.getLast().getId();
			member.updateLastReadMsgId(latestMsgId);
		}

		log.info("채팅 메시지 조회: roomId={}, userId={}", LogSanitizer.sanitize(String.valueOf(roomId)),
			LogSanitizer.sanitize(String.valueOf(userId)));

		return new ChatMessageListResponse(responses, member.getLastReadMsgId(), nextCursor, hasNext);
	}

	@Transactional
	public ChatMessageResponse sendMessage(Long senderId, ChatMessageRequest request, String chatSessionId) {
		ChatRoom chatRoom = chatRoomRepository.findByIdAndIsDeletedFalse(request.roomId())
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		chatMemberRepository.findByChatRoomIdAndUserId(request.roomId(), senderId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		User sender = userRepository.findByIdAndIsWithdrawFalse(senderId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		if (chatRoom.getType() == ChatRoomTypes.PRIVATE) {
			chatPrivateRoomRepository.findById(chatRoom.getId()).ifPresent(privateRoom -> {
				User otherUser = privateRoom.getUser1().getId().equals(senderId)
					? privateRoom.getUser2()
					: privateRoom.getUser1();

				Optional<ChatMember> otherMemberOpt = chatMemberRepository.findByChatRoomIdAndUserId(
					chatRoom.getId(), otherUser.getId());
				if (otherMemberOpt.isEmpty()) {
					ChatMember rejoinMember = ChatMember.builder()
						.chatRoom(chatRoom)
						.user(otherUser)
						.roomName(sender.getNickname())
						.build();
					chatMemberRepository.save(rejoinMember);
					chatRoom.incrementCount();
				}
			});
		}

		ChatMessageTypes messageType;
		try {
			messageType = ChatMessageTypes.valueOf(request.type());
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		String entityContent = (messageType == ChatMessageTypes.IMAGE)
			? request.s3Key()
			: request.content();

		ChatMessage chatMessage = ChatMessage.builder()
			.chatRoom(chatRoom)
			.sender(sender)
			.type(messageType)
			.content(entityContent)
			.build();

		chatMessageRepository.save(chatMessage);

		String lastMessagePreview = (messageType == ChatMessageTypes.IMAGE)
			? "[이미지]"
			: request.content();
		chatRoom.updateLastMessage(lastMessagePreview, chatMessage.getCreatedAt());

		ChatMessageResponse response = buildResponse(chatMessage, sender);

		redisPublisher.publish(request.roomId(), response, chatSessionId);

		ChatRoomNotification notification = new ChatRoomNotification(
			request.roomId(), lastMessagePreview, chatMessage.getCreatedAt());
		List<Long> memberUserIds = chatMemberRepository.findUserIdsByChatRoomId(request.roomId());
		for (Long memberUserId : memberUserIds) {
			redisPublisher.publishNotification(memberUserId, notification, chatSessionId);
		}

		log.info("채팅 메시지 저장: roomId={}, senderId={}, type={}", request.roomId(), senderId, messageType);

		return response;
	}

	@Transactional
	public void deleteMessage(Long userId, Long roomId, Long messageId) {
		chatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		chatMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		ChatMessage message = chatMessageRepository.findById(messageId)
			.filter(m -> m.getChatRoom().getId().equals(roomId))
			.orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));

		if (message.getSender() == null || !message.getSender().getId().equals(userId)) {
			throw new CustomException(ErrorCode.CHAT_MESSAGE_ACCESS_DENIED);
		}

		message.softDelete();

		log.info("채팅 메시지 삭제: roomId={}, messageId={}", LogSanitizer.sanitize(String.valueOf(roomId)),
			LogSanitizer.sanitize(String.valueOf(messageId)));
	}

	private Map<Long, String> fetchProfileImages(List<ChatMessage> messages) {
		List<Long> senderIds = messages.stream()
			.map(ChatMessage::getSender)
			.filter(Objects::nonNull)
			.map(User::getId)
			.distinct()
			.toList();

		if (senderIds.isEmpty()) {
			return Map.of();
		}

		return s3AttachmentRepository
			.findByRefTypeAndRefIdInAndIsDeletedFalse(RefType.USER, senderIds)
			.stream()
			.collect(Collectors.toMap(
				S3Attachment::getRefId,
				attachment -> s3StorageService.getPublicUrl(attachment.getS3Key()),
				(existing, replacement) -> existing
			));
	}

	private ChatMessageResponse buildHistoryResponse(ChatMessage message, Map<Long, String> profileImageMap) {
		boolean isDeleted = message.isDeleted();
		boolean isImage = message.getType() == ChatMessageTypes.IMAGE;

		ChatMessageResponse.Sender senderDto = null;
		if (message.getSender() != null) {
			User sender = message.getSender();
			String profileImage = profileImageMap.get(sender.getId());
			senderDto = new ChatMessageResponse.Sender(sender.getId(), sender.getNickname(), profileImage);
		}

		String content = isDeleted ? null : (isImage ? null : message.getContent());
		String imageUrl = isDeleted ? null : (isImage ? s3StorageService.getPublicUrl(message.getContent()) : null);

		return new ChatMessageResponse(
			message.getId(), senderDto, message.getType().name(),
			content, imageUrl, message.getCreatedAt(), isDeleted
		);
	}

	private ChatMessageResponse buildResponse(ChatMessage message, User sender) {
		boolean isImage = message.getType() == ChatMessageTypes.IMAGE;

		String profileImage = s3AttachmentRepository
			.findTopByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType.USER, sender.getId())
			.map(attachment -> s3StorageService.getPublicUrl(attachment.getS3Key()))
			.orElse(null);

		ChatMessageResponse.Sender senderDto = new ChatMessageResponse.Sender(
			sender.getId(),
			sender.getNickname(),
			profileImage
		);

		return new ChatMessageResponse(
			message.getId(),
			senderDto,
			message.getType().name(),
			isImage ? null : message.getContent(),
			isImage ? s3StorageService.getPublicUrl(message.getContent()) : null,
			message.getCreatedAt(),
			false
		);
	}
}
