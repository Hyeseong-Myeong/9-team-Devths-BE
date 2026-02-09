package com.ktb3.devths.board.dto.response;

import java.util.List;
import java.util.Map;

import com.ktb3.devths.board.domain.entity.Post;
import com.ktb3.devths.board.domain.entity.PostTag;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.user.domain.entity.UserInterest;

public record PostListResponse(
	List<PostSummaryResponse> posts,
	Long lastId,
	boolean hasNext
) {

	public static PostListResponse of(
		List<Post> postList,
		int requestedSize,
		Map<Long, List<PostTag>> tagMap,
		Map<Long, S3Attachment> profileImageMap,
		Map<Long, List<UserInterest>> interestMap,
		S3StorageService s3StorageService
	) {
		boolean hasNext = postList.size() > requestedSize;

		List<Post> actualPosts = hasNext
			? postList.subList(0, requestedSize)
			: postList;

		List<PostSummaryResponse> posts = actualPosts.stream()
			.map(post -> PostSummaryResponse.from(post, tagMap, profileImageMap, interestMap, s3StorageService))
			.toList();

		Long lastId = posts.isEmpty() ? null : posts.get(posts.size() - 1).postId();

		return new PostListResponse(posts, lastId, hasNext);
	}
}
