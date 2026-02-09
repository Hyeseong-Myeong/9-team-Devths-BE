package com.ktb3.devths.user.dto.response;

import java.time.LocalDateTime;

import com.ktb3.devths.board.domain.entity.Post;

public record MyPostSummaryResponse(
	Long id,
	String title,
	String content,
	int likeCount,
	int commentCount,
	int shareCount,
	LocalDateTime createdAt
) {
	public static MyPostSummaryResponse from(Post post) {
		return new MyPostSummaryResponse(
			post.getId(),
			post.getTitle(),
			post.getContent(),
			post.getLikeCount(),
			post.getCommentCount(),
			post.getShareCount(),
			post.getCreatedAt()
		);
	}
}
