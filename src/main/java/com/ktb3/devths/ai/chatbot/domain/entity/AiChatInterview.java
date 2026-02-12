package com.ktb3.devths.ai.chatbot.domain.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ktb3.devths.ai.chatbot.domain.constant.InterviewCompletionType;
import com.ktb3.devths.ai.chatbot.domain.constant.InterviewStatus;
import com.ktb3.devths.ai.chatbot.domain.constant.InterviewType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ai_chat_interview")
public class AiChatInterview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_id")
	private AiChatRoom room;

	@Column(name = "interview_type", nullable = false)
	@Enumerated(EnumType.STRING)
	private InterviewType interviewType;

	@Column(name = "current_question_count", nullable = false)
	private int currentQuestionCount = 0;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private InterviewStatus status = InterviewStatus.IN_PROGRESS;

	@Column(name = "completion_type")
	@Enumerated(EnumType.STRING)
	private InterviewCompletionType completionType;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	@LastModifiedDate
	private LocalDateTime updatedAt;

	public void incrementQuestionCount() {
		this.currentQuestionCount++;
	}

	public void complete(InterviewCompletionType completionType) {
		this.status = InterviewStatus.COMPLETED;
		this.completionType = completionType;
	}
}
