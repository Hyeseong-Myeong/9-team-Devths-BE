package com.ktb3.devths.user.dto.response;

import java.util.List;

import com.ktb3.devths.board.domain.entity.Comment;

public record MyCommentListResponse(
	List<MyCommentSummaryResponse> comments,
	boolean hasNext,
	Long lastId
) {

	public static MyCommentListResponse of(List<Comment> commentList, int requestedSize) {
		boolean hasNext = commentList.size() > requestedSize;

		List<Comment> actualComments = hasNext
			? commentList.subList(0, requestedSize)
			: commentList;

		List<MyCommentSummaryResponse> comments = actualComments.stream()
			.map(MyCommentSummaryResponse::from)
			.toList();

		Long lastId = comments.isEmpty() ? null : comments.getLast().id();

		return new MyCommentListResponse(comments, hasNext, lastId);
	}
}
