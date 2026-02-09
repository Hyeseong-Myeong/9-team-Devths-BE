package com.ktb3.devths.board.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.board.domain.entity.Comment;
import com.ktb3.devths.board.domain.entity.Post;
import com.ktb3.devths.board.dto.request.CommentCreateRequest;
import com.ktb3.devths.board.dto.request.CommentUpdateRequest;
import com.ktb3.devths.board.dto.response.CommentCreateResponse;
import com.ktb3.devths.board.dto.response.CommentListResponse;
import com.ktb3.devths.board.dto.response.CommentUpdateResponse;
import com.ktb3.devths.board.repository.CommentRepository;
import com.ktb3.devths.board.repository.PostRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.storage.domain.constant.RefType;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.repository.S3AttachmentRepository;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {

	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 100;

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final S3StorageService s3StorageService;

	@Transactional
	public CommentCreateResponse createComment(Long userId, Long postId, CommentCreateRequest request) {
		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		Post post = postRepository.findByIdAndIsDeletedFalseForUpdate(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

		Comment parent = resolveParent(request.parentId(), postId);

		Comment comment = commentRepository.save(Comment.builder()
			.post(post)
			.user(user)
			.parent(parent)
			.content(request.content())
			.build());

		post.incrementCommentCount();

		return CommentCreateResponse.from(comment);
	}

	@Transactional
	public CommentUpdateResponse updateComment(Long userId, Long postId, Long commentId,
		CommentUpdateRequest request) {
		Comment comment = commentRepository.findByIdAndIsDeletedFalseWithUser(commentId)
			.orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

		if (!comment.getPost().getId().equals(postId)) {
			throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
		}

		if (!comment.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.COMMENT_ACCESS_DENIED);
		}

		comment.updateContent(request.content());

		return CommentUpdateResponse.from(comment);
	}

	@Transactional
	public void deleteComment(Long userId, Long postId, Long commentId) {
		Comment comment = commentRepository.findByIdAndIsDeletedFalseWithUser(commentId)
			.orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

		if (!comment.getPost().getId().equals(postId)) {
			throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
		}

		if (!comment.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.COMMENT_ACCESS_DENIED);
		}

		comment.delete();

		Post post = postRepository.findByIdAndIsDeletedFalseForUpdate(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
		post.decrementCommentCount();
	}

	@Transactional(readOnly = true)
	public CommentListResponse getCommentList(Long postId, Integer size, Long lastId) {
		validatePostExists(postId);

		int pageSize = (size == null || size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		List<Comment> comments = (lastId != null)
			? commentRepository.findCommentsByPostIdAfterCursor(postId, lastId, pageable)
			: commentRepository.findCommentsByPostId(postId, pageable);

		if (comments.isEmpty()) {
			return CommentListResponse.of(comments, pageSize, Map.of(), s3StorageService);
		}

		List<Long> userIds = comments.stream()
			.map(comment -> comment.getUser().getId())
			.distinct()
			.toList();

		Map<Long, S3Attachment> profileImageMap = buildProfileImageMap(userIds);

		return CommentListResponse.of(comments, pageSize, profileImageMap, s3StorageService);
	}

	private Comment resolveParent(Long parentId, Long postId) {
		if (parentId == null) {
			return null;
		}

		Comment parent = commentRepository.findById(parentId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

		if (!parent.getPost().getId().equals(postId)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		if (parent.isDeleted()) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		return parent;
	}

	private void validatePostExists(Long postId) {
		postRepository.findByIdAndIsDeletedFalseWithUser(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
	}

	private Map<Long, S3Attachment> buildProfileImageMap(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return Collections.emptyMap();
		}

		return s3AttachmentRepository
			.findByRefTypeAndRefIdInAndIsDeletedFalse(RefType.USER, userIds)
			.stream()
			.collect(Collectors.toMap(
				S3Attachment::getRefId,
				attachment -> attachment,
				(first, second) -> first
			));
	}
}
