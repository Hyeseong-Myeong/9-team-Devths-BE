package com.ktb3.devths.chat.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.chat.domain.constant.ChatMessageTypes;
import com.ktb3.devths.chat.domain.entity.ChatMessage;
import com.ktb3.devths.chat.domain.entity.ChatRoom;
import com.ktb3.devths.chat.dto.request.ChatMessageRequest;
import com.ktb3.devths.chat.dto.response.ChatMessageResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomNotification;
import com.ktb3.devths.chat.repository.ChatMemberRepository;
import com.ktb3.devths.chat.repository.ChatMessageRepository;
import com.ktb3.devths.chat.repository.ChatRoomRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.storage.domain.constant.RefType;
import com.ktb3.devths.global.storage.repository.S3AttachmentRepository;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMemberRepository chatMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserRepository userRepository;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final RedisPublisher redisPublisher;

	@Transactional
	public ChatMessageResponse sendMessage(Long senderId, ChatMessageRequest request) {
		ChatRoom chatRoom = chatRoomRepository.findByIdAndIsDeletedFalse(request.roomId())
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		chatMemberRepository.findByChatRoomIdAndUserId(request.roomId(), senderId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		User sender = userRepository.findByIdAndIsWithdrawFalse(senderId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

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

		redisPublisher.publish(request.roomId(), response);

		ChatRoomNotification notification = new ChatRoomNotification(
			request.roomId(), lastMessagePreview, chatMessage.getCreatedAt());
		List<Long> memberUserIds = chatMemberRepository.findUserIdsByChatRoomId(request.roomId());
		for (Long memberUserId : memberUserIds) {
			redisPublisher.publishNotification(memberUserId, notification);
		}

		log.info("채팅 메시지 저장: roomId={}, senderId={}, type={}", request.roomId(), senderId, messageType);

		return response;
	}

	private ChatMessageResponse buildResponse(ChatMessage message, User sender) {
		boolean isImage = message.getType() == ChatMessageTypes.IMAGE;

		String profileImage = s3AttachmentRepository
			.findTopByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType.USER, sender.getId())
			.map(attachment -> attachment.getS3Key())
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
			isImage ? message.getContent() : null,
			message.getCreatedAt(),
			false
		);
	}
}
