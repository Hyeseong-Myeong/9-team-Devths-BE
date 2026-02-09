package com.ktb3.devths.board.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.ktb3.devths.board.domain.entity.Post;
import com.ktb3.devths.board.domain.entity.PostTag;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.domain.entity.UserInterest;

public record PostSummaryResponse(
	Long postId,
	String title,
	String previewContent,
	PostAuthorInfo user,
	int likeCount,
	int commentCount,
	int shareCount,
	List<String> tags,
	LocalDateTime createdAt
) {

	private static final int PREVIEW_MAX_LENGTH = 100;

	public static PostSummaryResponse from(
		Post post,
		Map<Long, List<PostTag>> tagMap,
		Map<Long, S3Attachment> profileImageMap,
		Map<Long, List<UserInterest>> interestMap,
		S3StorageService s3StorageService
	) {
		String content = post.getContent();
		String preview = (content == null) ? ""
			: (content.length() > PREVIEW_MAX_LENGTH ? content.substring(0, PREVIEW_MAX_LENGTH) : content);

		List<String> tags = tagMap.getOrDefault(post.getId(), List.of()).stream()
			.map(pt -> pt.getTagName().getDisplayName())
			.toList();

		PostAuthorInfo authorInfo = PostAuthorInfo.from(
			post.getUser(), profileImageMap, interestMap, s3StorageService
		);

		return new PostSummaryResponse(
			post.getId(),
			post.getTitle(),
			preview,
			authorInfo,
			post.getLikeCount(),
			post.getCommentCount(),
			post.getShareCount(),
			tags,
			post.getCreatedAt()
		);
	}

	public record PostAuthorInfo(
		Long userId,
		String nickname,
		String profileImage,
		List<String> interests
	) {
		public static PostAuthorInfo from(
			User user,
			Map<Long, S3Attachment> profileImageMap,
			Map<Long, List<UserInterest>> interestMap,
			S3StorageService s3StorageService
		) {
			S3Attachment attachment = profileImageMap.get(user.getId());
			String profileImageUrl = (attachment != null)
				? s3StorageService.getPublicUrl(attachment.getS3Key())
				: null;

			List<String> interests = interestMap.getOrDefault(user.getId(), List.of()).stream()
				.map(ui -> ui.getInterest().getDisplayName())
				.toList();

			return new PostAuthorInfo(
				user.getId(),
				user.getNickname(),
				profileImageUrl,
				interests
			);
		}
	}
}
