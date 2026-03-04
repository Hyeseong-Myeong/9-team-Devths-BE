package com.ktb3.devths.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.chat.domain.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	@Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender "
		+ "WHERE m.chatRoom.id = :roomId "
		+ "ORDER BY m.id DESC")
	List<ChatMessage> findByChatRoomIdOrderByIdDesc(
		@Param("roomId") Long roomId,
		Pageable pageable
	);

	@Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender "
		+ "WHERE m.chatRoom.id = :roomId "
		+ "AND m.id < :lastId "
		+ "ORDER BY m.id DESC")
	List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(
		@Param("roomId") Long roomId,
		@Param("lastId") Long lastId,
		Pageable pageable
	);

	@Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender "
		+ "WHERE m.chatRoom.id = :roomId AND m.createdAt >= :joinedAt "
		+ "ORDER BY m.id DESC")
	List<ChatMessage> findByChatRoomIdAndCreatedAtAfterOrderByIdDesc(
		@Param("roomId") Long roomId,
		@Param("joinedAt") LocalDateTime joinedAt,
		Pageable pageable
	);

	@Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender "
		+ "WHERE m.chatRoom.id = :roomId AND m.id < :lastId AND m.createdAt >= :joinedAt "
		+ "ORDER BY m.id DESC")
	List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
		@Param("roomId") Long roomId,
		@Param("lastId") Long lastId,
		@Param("joinedAt") LocalDateTime joinedAt,
		Pageable pageable
	);

	@Modifying
	@Query("DELETE FROM ChatMessage m WHERE m.chatRoom.id = :roomId")
	void deleteByChatRoomId(@Param("roomId") Long roomId);

	Optional<ChatMessage> findTopByChatRoomIdOrderByIdDesc(Long roomId);
}
