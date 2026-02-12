package com.ktb3.devths.chat.service;

import java.security.SecureRandom;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.chat.domain.constant.ChatRoomTypes;
import com.ktb3.devths.chat.domain.entity.ChatMember;
import com.ktb3.devths.chat.domain.entity.ChatPrivateRoom;
import com.ktb3.devths.chat.domain.entity.ChatRoom;
import com.ktb3.devths.chat.dto.response.PrivateChatRoomCreateResponse;
import com.ktb3.devths.chat.repository.ChatMemberRepository;
import com.ktb3.devths.chat.repository.ChatPrivateRoomRepository;
import com.ktb3.devths.chat.repository.ChatRoomRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

	private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final int INVITE_CODE_LENGTH = 8;
	private static final int MAX_INVITE_CODE_RETRY = 5;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatPrivateRoomRepository chatPrivateRoomRepository;
	private final ChatMemberRepository chatMemberRepository;
	private final UserRepository userRepository;

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
