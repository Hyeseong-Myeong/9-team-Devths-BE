package com.ktb3.devths.board.dto.response;

import com.ktb3.devths.board.domain.entity.Post;

public record PostUpdateResponse(
	Long postId
) {
	public static PostUpdateResponse from(Post post) {
		return new PostUpdateResponse(post.getId());
	}
}
