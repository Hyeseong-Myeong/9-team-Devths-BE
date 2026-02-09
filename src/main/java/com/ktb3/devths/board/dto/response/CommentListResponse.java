package com.ktb3.devths.board.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.ktb3.devths.board.domain.entity.Comment;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.user.domain.entity.User;

public record CommentListResponse(
	List<CommentItemResponse> comments,
	Long lastId,
	boolean hasNext
) {

	public static CommentListResponse of(
		List<Comment> commentList,
		int requestedSize,
		Map<Long, S3Attachment> profileImageMap,
		S3StorageService s3StorageService
	) {
		boolean hasNext = commentList.size() > requestedSize;

		List<Comment> actualComments = hasNext
			? commentList.subList(0, requestedSize)
			: commentList;

		List<CommentItemResponse> comments = actualComments.stream()
			.map(comment -> CommentItemResponse.from(comment, profileImageMap, s3StorageService))
			.toList();

		Long lastId = comments.isEmpty()
			? null
			: comments.get(comments.size() - 1).commentId();

		return new CommentListResponse(comments, lastId, hasNext);
	}

	public record CommentItemResponse(
		Long commentId,
		Long parentId,
		String content,
		CommentAuthorInfo user,
		LocalDateTime createdAt,
		boolean isDeleted
	) {

		public static CommentItemResponse from(
			Comment comment,
			Map<Long, S3Attachment> profileImageMap,
			S3StorageService s3StorageService
		) {
			CommentAuthorInfo authorInfo = CommentAuthorInfo.from(
				comment.getUser(), profileImageMap, s3StorageService);

			Long parentId = comment.getParent() != null
				? comment.getParent().getId()
				: null;

			return new CommentItemResponse(
				comment.getId(),
				parentId,
				comment.isDeleted() ? null : comment.getContent(),
				authorInfo,
				comment.getCreatedAt(),
				comment.isDeleted()
			);
		}
	}

	public record CommentAuthorInfo(
		Long userId,
		String nickname,
		String profileImage
	) {

		public static CommentAuthorInfo from(
			User user,
			Map<Long, S3Attachment> profileImageMap,
			S3StorageService s3StorageService
		) {
			S3Attachment attachment = profileImageMap.get(user.getId());
			String profileImageUrl = (attachment != null)
				? s3StorageService.getPublicUrl(attachment.getS3Key())
				: null;

			return new CommentAuthorInfo(
				user.getId(),
				user.getNickname(),
				profileImageUrl
			);
		}
	}
}
