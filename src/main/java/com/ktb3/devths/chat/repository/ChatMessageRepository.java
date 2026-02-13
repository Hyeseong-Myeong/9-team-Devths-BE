package com.ktb3.devths.chat.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
