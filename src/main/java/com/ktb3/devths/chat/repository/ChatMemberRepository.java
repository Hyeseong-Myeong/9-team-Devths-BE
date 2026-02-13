package com.ktb3.devths.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.chat.domain.constant.ChatRoomTypes;
import com.ktb3.devths.chat.domain.entity.ChatMember;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

	Optional<ChatMember> findByChatRoomIdAndUserId(Long roomId, Long userId);

	@Query("SELECT m FROM ChatMember m JOIN FETCH m.chatRoom r "
		+ "WHERE m.user.id = :userId AND r.type = :type AND r.isDeleted = false "
		+ "ORDER BY r.lastMessageAt DESC NULLS LAST, r.id DESC")
	List<ChatMember> findByUserIdAndType(
		@Param("userId") Long userId,
		@Param("type") ChatRoomTypes type,
		Pageable pageable
	);

	@Query("SELECT m FROM ChatMember m JOIN FETCH m.chatRoom r "
		+ "WHERE m.user.id = :userId AND r.type = :type AND r.isDeleted = false "
		+ "AND (r.lastMessageAt < :cursor OR r.lastMessageAt IS NULL) "
		+ "ORDER BY r.lastMessageAt DESC NULLS LAST, r.id DESC")
	List<ChatMember> findByUserIdAndTypeAfterCursor(
		@Param("userId") Long userId,
		@Param("type") ChatRoomTypes type,
		@Param("cursor") LocalDateTime cursor,
		Pageable pageable
	);

	@Query("SELECT m.user.id FROM ChatMember m WHERE m.chatRoom.id = :roomId")
	List<Long> findUserIdsByChatRoomId(@Param("roomId") Long roomId);
}
