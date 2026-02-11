package com.ktb3.devths.chat.domain.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ktb3.devths.chat.domain.constant.ChatRoomTags;
import com.ktb3.devths.chat.domain.constant.ChatRoomTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "chat_rooms")
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "type", nullable = false)
	@Enumerated(EnumType.STRING)
	private ChatRoomTypes type;

	@Column(name = "title")
	private String title;

	@Column(name = "tag")
	@Enumerated(EnumType.STRING)
	private ChatRoomTags tag;

	@Builder.Default
	@Column(name = "current_count", nullable = false)
	private Integer currentCount = 1;

	@Column(name = "last_message_content", columnDefinition = "TEXT")
	private String lastMessageContent;

	@Column(name = "last_message_at")
	private LocalDateTime lastMessageAt;

	@Column(name = "invite_code", nullable = false, unique = true)
	private String inviteCode;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	@Builder.Default
	@Column(name = "is_deleted", nullable = false)
	private boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;
}
