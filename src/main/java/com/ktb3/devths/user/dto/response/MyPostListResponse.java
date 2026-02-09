package com.ktb3.devths.user.dto.response;

import java.util.List;

import com.ktb3.devths.board.domain.entity.Post;

public record MyPostListResponse(
	List<MyPostSummaryResponse> posts,
	boolean hasNext,
	Long lastId
) {
	public static MyPostListResponse of(List<Post> postList, int requestedSize) {
		boolean hasNext = postList.size() > requestedSize;

		List<Post> actualPosts = hasNext
			? postList.subList(0, requestedSize)
			: postList;

		List<MyPostSummaryResponse> posts = actualPosts.stream()
			.map(MyPostSummaryResponse::from)
			.toList();

		Long lastId = posts.isEmpty() ? null : posts.getLast().id();

		return new MyPostListResponse(posts, hasNext, lastId);
	}
}
