package com.ktb3.devths.board.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.board.domain.constant.PostTags;
import com.ktb3.devths.board.domain.entity.Like;
import com.ktb3.devths.board.domain.entity.Post;
import com.ktb3.devths.board.domain.entity.PostTag;
import com.ktb3.devths.board.dto.request.PostCreateRequest;
import com.ktb3.devths.board.dto.request.PostUpdateRequest;
import com.ktb3.devths.board.dto.response.PostCreateResponse;
import com.ktb3.devths.board.dto.response.PostDetailResponse;
import com.ktb3.devths.board.dto.response.PostLikeResponse;
import com.ktb3.devths.board.dto.response.PostListResponse;
import com.ktb3.devths.board.dto.response.PostSummaryResponse;
import com.ktb3.devths.board.dto.response.PostUpdateResponse;
import com.ktb3.devths.board.repository.LikeRepository;
import com.ktb3.devths.board.repository.PostRepository;
import com.ktb3.devths.board.repository.PostTagRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.storage.domain.constant.RefType;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.repository.S3AttachmentRepository;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.user.domain.constant.Interests;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.domain.entity.UserInterest;
import com.ktb3.devths.user.repository.UserInterestRepository;
import com.ktb3.devths.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final PostRepository postRepository;
	private final PostTagRepository postTagRepository;
	private final LikeRepository likeRepository;
	private final UserRepository userRepository;
	private final UserInterestRepository userInterestRepository;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final S3StorageService s3StorageService;
	private final PostDetailCacheService postDetailCacheService;

	@Transactional
	public PostCreateResponse createPost(Long userId, PostCreateRequest request) {
		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		Post post = postRepository.save(Post.builder()
			.user(user)
			.title(request.title())
			.content(request.content())
			.build());

		saveTags(post, request.tags());
		linkAttachments(post.getId(), userId, request.fileIds());

		return PostCreateResponse.from(post);
	}

	@Transactional
	public PostUpdateResponse updatePost(Long userId, Long postId, PostUpdateRequest request) {
		Post post = postRepository.findByIdAndIsDeletedFalseWithUser(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

		if (!post.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.POST_ACCESS_DENIED);
		}

		post.updateTitle(request.title());
		post.updateContent(request.content());

		postTagRepository.deleteAllByPostId(postId);
		saveTags(post, request.tags());

		unlinkExistingAttachments(postId);
		linkAttachments(postId, userId, request.fileIds());
		postDetailCacheService.evictByPostId(postId);

		return PostUpdateResponse.from(post);
	}

	@Transactional(readOnly = true)
	public PostListResponse getPostList(Integer size, Long lastId, String tag) {
		int pageSize = (size == null || size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		String keyword = (tag != null) ? tag.strip() : null;
		boolean hasKeyword = keyword != null && !keyword.isEmpty();

		List<Post> posts = fetchPosts(keyword, hasKeyword, lastId, pageable);

		if (posts.isEmpty()) {
			return PostListResponse.of(posts, pageSize, Map.of(), Map.of(), Map.of(), s3StorageService);
		}

		boolean hasNext = posts.size() > pageSize;
		List<Post> actualPosts = hasNext ? posts.subList(0, pageSize) : posts;

		List<Long> postIds = actualPosts.stream()
			.map(Post::getId)
			.toList();

		List<Long> userIds = actualPosts.stream()
			.map(post -> post.getUser().getId())
			.distinct()
			.toList();

		Map<Long, List<PostTag>> tagMap = postTagRepository.findByPostIdIn(postIds).stream()
			.collect(Collectors.groupingBy(pt -> pt.getPost().getId()));

		Map<Long, S3Attachment> profileImageMap = buildProfileImageMap(userIds);

		Map<Long, List<UserInterest>> interestMap = userInterestRepository.findByUserIdIn(userIds).stream()
			.collect(Collectors.groupingBy(ui -> ui.getUser().getId()));

		return PostListResponse.of(posts, pageSize, tagMap, profileImageMap, interestMap, s3StorageService);
	}

	@Transactional(readOnly = true)
	public PostDetailResponse getPostDetail(Long userId, Long postId) {
		Optional<PostDetailResponse> cached = postDetailCacheService.get(postId, userId);
		if (cached.isPresent()) {
			return cached.get();
		}

		Post post = postRepository.findByIdAndIsDeletedFalseWithUser(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

		User author = post.getUser();
		Long authorId = author.getId();

		List<S3Attachment> attachments = s3AttachmentRepository
			.findByRefTypeAndRefIdAndIsDeletedFalseOrderBySortOrderAsc(RefType.POST, postId);

		List<PostTag> postTags = postTagRepository.findByPostIdIn(List.of(postId));

		S3Attachment profileImage = s3AttachmentRepository
			.findTopByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType.USER, authorId)
			.orElse(null);

		String profileImageUrl = (profileImage != null)
			? s3StorageService.getPublicUrl(profileImage.getS3Key())
			: null;

		List<String> interests = userInterestRepository.findInterestsByUserId(authorId).stream()
			.map(Interests::getDisplayName)
			.toList();

		PostSummaryResponse.PostAuthorInfo authorInfo = new PostSummaryResponse.PostAuthorInfo(
			authorId, author.getNickname(), profileImageUrl, interests
		);

		boolean isLiked = likeRepository.existsByPostIdAndUserId(postId, userId);

		PostDetailResponse response = PostDetailResponse.of(post, attachments, postTags, authorInfo, isLiked,
			s3StorageService);
		postDetailCacheService.put(postId, userId, response);

		return response;
	}

	@Transactional
	public void deletePost(Long userId, Long postId) {
		Post post = postRepository.findByIdAndIsDeletedFalseWithUser(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

		if (!post.getUser().getId().equals(userId)) {
			throw new CustomException(ErrorCode.POST_ACCESS_DENIED);
		}

		post.delete();
		postDetailCacheService.evictByPostId(postId);
	}

	@Transactional
	public PostLikeResponse likePost(Long userId, Long postId) {
		Post post = postRepository.findByIdAndIsDeletedFalseForUpdate(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

		boolean alreadyLiked = likeRepository.existsByPostIdAndUserId(postId, userId);
		if (alreadyLiked) {
			return PostLikeResponse.from(post);
		}

		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		Like like = Like.builder()
			.post(post)
			.user(user)
			.build();

		likeRepository.save(like);
		post.incrementLikeCount();
		postDetailCacheService.evictByPostId(postId);

		return PostLikeResponse.from(post);
	}

	@Transactional
	public void unlikePost(Long userId, Long postId) {
		Post post = postRepository.findByIdAndIsDeletedFalseForUpdate(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

		Like like = likeRepository.findByPostIdAndUserId(postId, userId)
			.orElse(null);

		if (like == null) {
			return;
		}

		likeRepository.delete(like);
		post.decrementLikeCount();
		postDetailCacheService.evictByPostId(postId);
	}

	private List<Post> fetchPosts(String keyword, boolean hasKeyword, Long lastId, Pageable pageable) {
		if (hasKeyword && lastId != null) {
			return postRepository.findPostsByKeywordNotDeletedAfterCursor(keyword, lastId, pageable);
		}
		if (hasKeyword) {
			return postRepository.findPostsByKeywordNotDeleted(keyword, pageable);
		}
		if (lastId != null) {
			return postRepository.findPostsNotDeletedAfterCursor(lastId, pageable);
		}
		return postRepository.findPostsNotDeleted(pageable);
	}

	private void saveTags(Post post, List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return;
		}

		List<PostTag> postTags = tags.stream()
			.distinct()
			.map(displayName -> {
				PostTags tag = PostTags.fromDisplayName(displayName);
				if (tag == null) {
					throw new CustomException(ErrorCode.INVALID_INPUT);
				}
				return PostTag.builder()
					.post(post)
					.tagName(tag)
					.build();
			})
			.toList();

		postTagRepository.saveAll(postTags);
	}

	private void unlinkExistingAttachments(Long postId) {
		List<S3Attachment> existing = s3AttachmentRepository
			.findByRefTypeAndRefIdAndIsDeletedFalseOrderBySortOrderAsc(RefType.POST, postId);

		for (S3Attachment attachment : existing) {
			attachment.updateRefId(null);
		}
	}

	private void linkAttachments(Long postId, Long userId, List<Long> fileIds) {
		if (fileIds == null || fileIds.isEmpty()) {
			return;
		}

		List<S3Attachment> attachments = s3AttachmentRepository.findByIdInAndIsDeletedFalse(fileIds);

		if (attachments.size() != fileIds.size()) {
			throw new CustomException(ErrorCode.INVALID_FILE_REFERENCE);
		}

		for (S3Attachment attachment : attachments) {
			if (!attachment.getUser().getId().equals(userId)) {
				throw new CustomException(ErrorCode.INVALID_FILE_REFERENCE);
			}
			attachment.updateRefId(postId);
		}
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
