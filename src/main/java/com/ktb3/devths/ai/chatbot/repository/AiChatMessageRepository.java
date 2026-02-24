package com.ktb3.devths.ai.chatbot.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ktb3.devths.ai.chatbot.domain.entity.AiChatMessage;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

	@Query("SELECT m FROM AiChatMessage m " + "WHERE m.room.id = :roomId " + "ORDER BY m.id DESC")
	List<AiChatMessage> findByRoomIdOrderByIdDesc(
		@Param("roomId") Long roomId,
		Pageable pageable
	);

	@Query("SELECT m FROM AiChatMessage m " + "WHERE m.room.id = :roomId " + "AND m.id < :lastId " + "ORDER BY m.id DESC")
	List<AiChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(
		@Param("roomId") Long roomId,
		@Param("lastId") Long lastId,
		Pageable pageable
	);

	@Query("SELECT m FROM AiChatMessage m WHERE m.interview.id = :interviewId ORDER BY m.id ASC")
	List<AiChatMessage> findByInterviewIdOrderByIdAsc(@Param("interviewId") Long interviewId);
}
