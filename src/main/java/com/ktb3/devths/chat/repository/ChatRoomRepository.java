package com.ktb3.devths.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ktb3.devths.chat.domain.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
	boolean existsByInviteCode(String inviteCode);

	Optional<ChatRoom> findByIdAndIsDeletedFalse(Long id);
}
