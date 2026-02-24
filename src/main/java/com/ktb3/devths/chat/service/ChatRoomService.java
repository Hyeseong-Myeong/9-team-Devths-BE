package com.ktb3.devths.chat.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.chat.domain.constant.ChatRoomTypes;
import com.ktb3.devths.chat.domain.entity.ChatMember;
import com.ktb3.devths.chat.domain.entity.ChatPrivateRoom;
import com.ktb3.devths.chat.domain.entity.ChatRoom;
import com.ktb3.devths.chat.dto.request.ChatRoomUpdateRequest;
import com.ktb3.devths.chat.dto.response.ChatReadUpdateResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomDetailResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomListResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomSummaryResponse;
import com.ktb3.devths.chat.dto.response.ChatRoomUpdateResponse;
import com.ktb3.devths.chat.dto.response.PrivateChatRoomCreateResponse;
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
public class ChatRoomService {

	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 100;
	private static final int RECENT_IMAGES_LIMIT = 4;
	private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final int INVITE_CODE_LENGTH = 8;
	private static final int MAX_INVITE_CODE_RETRY = 5;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatPrivateRoomRepository chatPrivateRoomRepository;
	private final ChatMemberRepository chatMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final S3StorageService s3StorageService;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public ChatRoomListResponse getChatRoomList(Long userId, String type, Integer size, LocalDateTime cursor) {
		ChatRoomTypes roomType;
		try {
			roomType = ChatRoomTypes.valueOf(type);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		int pageSize = (size == null || size <= 0)
			? DEFAULT_PAGE_SIZE
			: Math.min(size, MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		List<ChatMember> members = (cursor == null)
			? chatMemberRepository.findByUserIdAndType(userId, roomType, pageable)
			: chatMemberRepository.findByUserIdAndTypeAfterCursor(userId, roomType, cursor, pageable);

		boolean hasNext = members.size() > pageSize;
		List<ChatMember> actualMembers = hasNext
			? members.subList(0, pageSize)
			: members;

		Map<Long, String> profileImageMap = Map.of();
		Map<Long, String> roomTitleMap = Map.of();
		if (roomType == ChatRoomTypes.PRIVATE) {
			List<Long> roomIds = actualMembers.stream()
				.map(m -> m.getChatRoom().getId())
				.toList();

			Map<Long, Long> roomToOtherUserMap = chatPrivateRoomRepository.findByRoomIdIn(roomIds).stream()
				.collect(Collectors.toMap(
					pr -> pr.getRoom().getId(),
					pr -> pr.getUser1().getId().equals(userId)
						? pr.getUser2().getId()
						: pr.getUser1().getId()
				));

			List<Long> otherUserIds = roomToOtherUserMap.values().stream().distinct().toList();
			if (!otherUserIds.isEmpty()) {
				Map<Long, String> userNicknameMap = userRepository.findAllById(otherUserIds).stream()
					.collect(Collectors.toMap(
						User::getId,
						User::getNickname
					));

				roomTitleMap = roomToOtherUserMap.entrySet().stream()
					.filter(e -> userNicknameMap.containsKey(e.getValue()))
					.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> userNicknameMap.get(e.getValue())
					));

				Map<Long, String> userProfileMap = s3AttachmentRepository
					.findByRefTypeAndRefIdInAndIsDeletedFalse(RefType.USER, otherUserIds)
					.stream()
					.collect(Collectors.toMap(
						S3Attachment::getRefId,
						a -> s3StorageService.getPublicUrl(a.getS3Key()),
						(existing, replacement) -> existing
					));

				profileImageMap = roomToOtherUserMap.entrySet().stream()
					.filter(e -> userProfileMap.containsKey(e.getValue()))
					.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> userProfileMap.get(e.getValue())
					));
			}
		}

		Map<Long, String> finalProfileImageMap = profileImageMap;
		Map<Long, String> finalRoomTitleMap = roomTitleMap;
		List<ChatRoomSummaryResponse> chatRooms = actualMembers.stream()
			.map(member -> {
				ChatRoom room = member.getChatRoom();
				String title = roomType == ChatRoomTypes.PRIVATE
					? finalRoomTitleMap.getOrDefault(room.getId(), member.getRoomName())
					: member.getRoomName();
				return new ChatRoomSummaryResponse(
					room.getId(),
					title,
					finalProfileImageMap.get(room.getId()),
					room.getLastMessageContent(),
					room.getLastMessageAt(),
					room.getCurrentCount(),
					room.getTag() != null ? room.getTag().name() : null
				);
			})
			.toList();

		LocalDateTime nextCursor = chatRooms.isEmpty()
			? null
			: chatRooms.getLast().lastMessageAt();

		return new ChatRoomListResponse(chatRooms, nextCursor, hasNext);
	}

	@Transactional
	public PrivateChatRoomCreateResponse createPrivateChatRoom(Long currentUserId, Long targetUserId) {
		if (currentUserId.equals(targetUserId)) {
			throw new CustomException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
		}

		User targetUser = userRepository.findByIdAndIsWithdrawFalse(targetUserId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		User currentUser = userRepository.findByIdAndIsWithdrawFalse(currentUserId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		String dmKey = ChatPrivateRoom.computeDmKey(currentUserId, targetUserId);

		Optional<ChatPrivateRoom> existingRoom = chatPrivateRoomRepository.findByDmKey(dmKey);
		if (existingRoom.isPresent()) {
			ChatRoom room = existingRoom.get().getRoom();

			Optional<ChatMember> currentMemberOpt = chatMemberRepository.findByChatRoomIdAndUserId(
				room.getId(), currentUserId);
			if (currentMemberOpt.isEmpty()) {
				ChatMember rejoinMember = ChatMember.builder()
					.chatRoom(room)
					.user(currentUser)
					.roomName(targetUser.getNickname())
					.build();
				chatMemberRepository.save(rejoinMember);
				room.incrementCount();
			}

			log.info("기존 1:1 채팅방 반환: roomId={}, dmKey={}", room.getId(), dmKey);
			return new PrivateChatRoomCreateResponse(
				room.getId(),
				false,
				ChatRoomTypes.PRIVATE.name(),
				targetUser.getNickname(),
				room.getInviteCode(),
				room.getCreatedAt()
			);
		}

		String inviteCode = generateUniqueInviteCode();

		ChatRoom chatRoom = ChatRoom.builder()
			.type(ChatRoomTypes.PRIVATE)
			.currentCount(2)
			.inviteCode(inviteCode)
			.build();
		chatRoomRepository.save(chatRoom);

		ChatPrivateRoom privateRoom = ChatPrivateRoom.builder()
			.room(chatRoom)
			.user1(currentUserId < targetUserId ? currentUser : targetUser)
			.user2(currentUserId < targetUserId ? targetUser : currentUser)
			.dmKey(dmKey)
			.build();
		chatPrivateRoomRepository.save(privateRoom);

		ChatMember currentMember = ChatMember.builder()
			.chatRoom(chatRoom)
			.user(currentUser)
			.roomName(targetUser.getNickname())
			.build();

		ChatMember targetMember = ChatMember.builder()
			.chatRoom(chatRoom)
			.user(targetUser)
			.roomName(currentUser.getNickname())
			.build();

		chatMemberRepository.save(currentMember);
		chatMemberRepository.save(targetMember);

		log.info("1:1 채팅방 생성: roomId={}, dmKey={}, currentUserId={}, targetUserId={}",
			chatRoom.getId(), dmKey, currentUserId, targetUserId);

		return new PrivateChatRoomCreateResponse(
			chatRoom.getId(),
			true,
			ChatRoomTypes.PRIVATE.name(),
			targetUser.getNickname(),
			chatRoom.getInviteCode(),
			chatRoom.getCreatedAt()
		);
	}

	@Transactional(readOnly = true)
	public ChatRoomDetailResponse getChatRoomDetail(Long userId, Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		Pageable imagePage = PageRequest.of(0, RECENT_IMAGES_LIMIT);
		List<ChatRoomDetailResponse.RecentImage> recentImages = s3AttachmentRepository
			.findByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType.CHATROOM, roomId, imagePage)
			.stream()
			.map(attachment -> new ChatRoomDetailResponse.RecentImage(
				attachment.getId(),
				s3StorageService.getPublicUrl(attachment.getS3Key()),
				attachment.getOriginalName(),
				attachment.getCreatedAt()
			))
			.toList();

		return new ChatRoomDetailResponse(
			chatRoom.getId(),
			chatRoom.getType().name(),
			chatRoom.getTitle(),
			member.isAlarmOn(),
			member.getRoomName(),
			chatRoom.getInviteCode(),
			chatRoom.getCreatedAt(),
			recentImages
		);
	}

	@Transactional
	public ChatRoomUpdateResponse updateChatRoom(Long userId, Long roomId, ChatRoomUpdateRequest request) {
		ChatRoom chatRoom = chatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		if (request.roomName() != null && chatRoom.getType() == ChatRoomTypes.PRIVATE) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		if (request.roomName() != null) {
			member.updateRoomName(request.roomName());
		}

		member.updateAlarmOn(request.isAlarmOn());

		return new ChatRoomUpdateResponse(chatRoom.getId(), member.getRoomName());
	}

	@Transactional
	public ChatReadUpdateResponse updateLastReadMsgId(Long userId, Long roomId, Long lastReadMsgId) {
		chatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		member.updateLastReadMsgId(lastReadMsgId);

		log.info("채팅 읽음 정보 갱신: roomId={}, userId={}", LogSanitizer.sanitize(String.valueOf(roomId)),
			LogSanitizer.sanitize(String.valueOf(userId)));

		return new ChatReadUpdateResponse(roomId, lastReadMsgId);
	}

	@Transactional
	public void leaveChatRoom(Long userId, Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findByIdAndIsDeletedFalse(roomId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

		ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
			.orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_ACCESS_DENIED));

		chatMemberRepository.delete(member);
		chatRoom.decrementCount();

		if (chatRoom.getCurrentCount() <= 0) {
			chatMessageRepository.deleteByChatRoomId(roomId);
			chatPrivateRoomRepository.deleteById(roomId);
			chatRoomRepository.delete(chatRoom);
		}
	}

	private String generateUniqueInviteCode() {
		SecureRandom random = new SecureRandom();
		for (int attempt = 0; attempt < MAX_INVITE_CODE_RETRY; attempt++) {
			StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
			for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
				sb.append(INVITE_CODE_CHARACTERS.charAt(random.nextInt(INVITE_CODE_CHARACTERS.length())));
			}
			String code = sb.toString();
			try {
				// unique constraint에 의존하여 중복 검증
				if (!chatRoomRepository.existsByInviteCode(code)) {
					return code;
				}
			} catch (Exception e) {
				log.warn("초대 코드 생성 충돌, 재시도: attempt={}", attempt + 1);
			}
		}
		throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
	}
}
