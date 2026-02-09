package com.ktb3.devths.board.dto.response;

import com.ktb3.devths.board.domain.entity.Comment;

public record CommentUpdateResponse(
	Long commentId
) {

	public static CommentUpdateResponse from(Comment comment) {
		return new CommentUpdateResponse(comment.getId());
	}
}
