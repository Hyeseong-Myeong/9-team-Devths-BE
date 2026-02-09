package com.ktb3.devths.user.dto.response;

import java.time.LocalDateTime;

import com.ktb3.devths.board.domain.entity.Comment;

public record MyCommentSummaryResponse(
	Long id,
	Long postId,
	String postTitle,
	String content,
	LocalDateTime createdAt
) {

	public static MyCommentSummaryResponse from(Comment comment) {
		return new MyCommentSummaryResponse(
			comment.getId(),
			comment.getPost().getId(),
			comment.getPost().getTitle(),
			comment.getContent(),
			comment.getCreatedAt()
		);
	}
}
