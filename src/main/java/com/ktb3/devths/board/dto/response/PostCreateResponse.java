package com.ktb3.devths.board.dto.response;

import com.ktb3.devths.board.domain.entity.Post;

public record PostCreateResponse(
	Long postId
) {
	public static PostCreateResponse from(Post post) {
		return new PostCreateResponse(post.getId());
	}
}
