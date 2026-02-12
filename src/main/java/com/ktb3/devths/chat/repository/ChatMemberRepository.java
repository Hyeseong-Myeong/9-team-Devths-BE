package com.ktb3.devths.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ktb3.devths.chat.domain.entity.ChatMember;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
}
