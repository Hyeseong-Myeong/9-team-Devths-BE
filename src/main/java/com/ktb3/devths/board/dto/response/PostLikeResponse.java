package com.ktb3.devths.board.dto.response;

import com.ktb3.devths.board.domain.entity.Post;

public record PostLikeResponse(
	Long postId,
	int likeCount
) {
	public static PostLikeResponse from(Post post) {
		return new PostLikeResponse(post.getId(), post.getLikeCount());
	}
}
