package com.ktb3.devths.board.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.ktb3.devths.board.domain.entity.Post;
import com.ktb3.devths.board.domain.entity.PostTag;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.service.S3StorageService;

public record PostDetailResponse(
	Long postId,
	String title,
	String content,
	List<AttachmentInfo> attachments,
	PostSummaryResponse.PostAuthorInfo user,
	int likeCount,
	int commentCount,
	int shareCount,
	List<String> tags,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	boolean isLiked
) {

	public static PostDetailResponse of(
		Post post,
		List<S3Attachment> attachments,
		List<PostTag> postTags,
		PostSummaryResponse.PostAuthorInfo authorInfo,
		boolean isLiked,
		S3StorageService s3StorageService
	) {
		List<AttachmentInfo> attachmentInfos = attachments.stream()
			.map(a -> AttachmentInfo.from(a, s3StorageService))
			.toList();

		List<String> tags = postTags.stream()
			.map(pt -> pt.getTagName().getDisplayName())
			.toList();

		return new PostDetailResponse(
			post.getId(),
			post.getTitle(),
			post.getContent(),
			attachmentInfos,
			authorInfo,
			post.getLikeCount(),
			post.getCommentCount(),
			post.getShareCount(),
			tags,
			post.getCreatedAt(),
			post.getUpdatedAt(),
			isLiked
		);
	}

	public record AttachmentInfo(
		Long fileId,
		String fileUrl,
		String fileName,
		long fileSize,
		String fileType,
		int sortOrder
	) {
		public static AttachmentInfo from(S3Attachment attachment, S3StorageService s3StorageService) {
			return new AttachmentInfo(
				attachment.getId(),
				s3StorageService.getPublicUrl(attachment.getS3Key()),
				attachment.getOriginalName(),
				attachment.getFileSize(),
				resolveFileType(attachment.getMimeType()),
				attachment.getSortOrder()
			);
		}

		private static String resolveFileType(String mimeType) {
			if (mimeType == null) {
				return "FILE";
			}
			if (mimeType.startsWith("image/")) {
				return "IMAGE";
			}
			if (mimeType.startsWith("video/")) {
				return "VIDEO";
			}
			return "FILE";
		}
	}
}
