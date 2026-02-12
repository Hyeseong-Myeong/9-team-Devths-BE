package com.ktb3.devths.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ktb3.devths.chat.domain.entity.ChatPrivateRoom;

public interface ChatPrivateRoomRepository extends JpaRepository<ChatPrivateRoom, Long> {
	Optional<ChatPrivateRoom> findByDmKey(String dmKey);
}
