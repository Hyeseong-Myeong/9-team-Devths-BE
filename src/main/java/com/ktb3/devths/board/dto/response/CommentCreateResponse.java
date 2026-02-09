package com.ktb3.devths.board.dto.response;

import com.ktb3.devths.board.domain.entity.Comment;

public record CommentCreateResponse(
	Long commentId
) {

	public static CommentCreateResponse from(Comment comment) {
		return new CommentCreateResponse(comment.getId());
	}
}
