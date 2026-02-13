package com.ktb3.devths.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ktb3.devths.chat.domain.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
}
