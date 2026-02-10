package com.ktb3.devths.user.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.storage.domain.constant.RefType;
import com.ktb3.devths.global.storage.repository.S3AttachmentRepository;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.user.domain.entity.Follow;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.domain.entity.UserStat;
import com.ktb3.devths.user.dto.response.FollowResponse;
import com.ktb3.devths.user.dto.response.FollowerListResponse;
import com.ktb3.devths.user.dto.response.FollowerSummaryResponse;
import com.ktb3.devths.user.repository.FollowRepository;
import com.ktb3.devths.user.repository.UserRepository;
import com.ktb3.devths.user.repository.UserStatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

	private static final int DEFAULT_FOLLOWER_PAGE_SIZE = 10;
	private static final int MAX_FOLLOWER_PAGE_SIZE = 100;

	private final UserRepository userRepository;
	private final FollowRepository followRepository;
	private final UserStatRepository userStatRepository;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final S3StorageService s3StorageService;

	@Transactional
	public FollowResponse follow(Long followerId, Long followingId) {
		if (followerId.equals(followingId)) {
			throw new CustomException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
		}

		User followingUser = userRepository.findByIdAndIsWithdrawFalse(followingId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		boolean alreadyFollowing = followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
		if (alreadyFollowing) {
			UserStat followerStat = getOrCreateUserStat(followerId);
			return FollowResponse.of(followingId, followerStat.getFollowingCount());
		}

		User followerUser = userRepository.findByIdAndIsWithdrawFalse(followerId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		Follow follow = Follow.builder()
			.follower(followerUser)
			.following(followingUser)
			.build();

		try {
			followRepository.save(follow);
		} catch (DataIntegrityViolationException e) {
			UserStat followerStat = getOrCreateUserStat(followerId);
			return FollowResponse.of(followingId, followerStat.getFollowingCount());
		}

		// 데드락 방지: userId 순서로 잠금
		UserStat firstStat;
		UserStat secondStat;

		if (followerId < followingId) {
			firstStat = getOrCreateUserStatForUpdate(followerUser);
			secondStat = getOrCreateUserStatForUpdate(followingUser);
			firstStat.incrementFollowingCount();
			secondStat.incrementFollowerCount();
		} else {
			firstStat = getOrCreateUserStatForUpdate(followingUser);
			secondStat = getOrCreateUserStatForUpdate(followerUser);
			firstStat.incrementFollowerCount();
			secondStat.incrementFollowingCount();
		}

		UserStat followerStat = followerId < followingId ? firstStat : secondStat;

		log.info("팔로우 성공: followerId={}, followingId={}", followerId, followingId);

		return FollowResponse.of(followingId, followerStat.getFollowingCount());
	}

	@Transactional
	public void unfollow(Long followerId, Long followingId) {
		if (followerId.equals(followingId)) {
			throw new CustomException(ErrorCode.SELF_UNFOLLOW_NOT_ALLOWED);
		}

		userRepository.findByIdAndIsWithdrawFalse(followingId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
			.orElse(null);

		if (follow == null) {
			return;
		}

		followRepository.delete(follow);

		// 데드락 방지: userId 순서로 잠금
		if (followerId < followingId) {
			getOrCreateUserStatForUpdate(followerId).decrementFollowingCount();
			getOrCreateUserStatForUpdate(followingId).decrementFollowerCount();
		} else {
			getOrCreateUserStatForUpdate(followingId).decrementFollowerCount();
			getOrCreateUserStatForUpdate(followerId).decrementFollowingCount();
		}

		log.info("언팔로우 성공: followerId={}, followingId={}", followerId, followingId);
	}

	@Transactional(readOnly = true)
	public FollowerListResponse getMyFollowers(Long userId, Integer size, Long lastId) {
		int pageSize = (size == null || size <= 0)
			? DEFAULT_FOLLOWER_PAGE_SIZE
			: Math.min(size, MAX_FOLLOWER_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		List<Follow> follows = (lastId == null)
			? followRepository.findFollowersByUserId(userId, pageable)
			: followRepository.findFollowersByUserIdAfterCursor(userId, lastId, pageable);

		boolean hasNext = follows.size() > pageSize;
		List<Follow> actualFollows = hasNext
			? follows.subList(0, pageSize)
			: follows;

		List<Long> followerUserIds = actualFollows.stream()
			.map(f -> f.getFollower().getId())
			.toList();

		Set<Long> followingBackIds = followerUserIds.isEmpty()
			? new HashSet<>()
			: new HashSet<>(followRepository.findFollowingIdsByFollowerIdAndFollowingIdIn(userId, followerUserIds));

		List<FollowerSummaryResponse> followers = actualFollows.stream()
			.map(f -> {
				User follower = f.getFollower();
				String profileImageUrl = s3AttachmentRepository
					.findTopByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType.USER, follower.getId())
					.map(attachment -> s3StorageService.getPublicUrl(attachment.getS3Key()))
					.orElse(null);

				return new FollowerSummaryResponse(
					f.getId(),
					follower.getId(),
					follower.getNickname(),
					profileImageUrl,
					followingBackIds.contains(follower.getId())
				);
			})
			.toList();

		Long nextLastId = followers.isEmpty() ? null : followers.getLast().id();

		return new FollowerListResponse(followers, hasNext, nextLastId);
	}

	private UserStat getOrCreateUserStat(Long userId) {
		return userStatRepository.findByUserId(userId)
			.orElseGet(() -> {
				User user = userRepository.getReferenceById(userId);
				return userStatRepository.save(UserStat.builder().user(user).build());
			});
	}

	private UserStat getOrCreateUserStatForUpdate(User user) {
		return getOrCreateUserStatForUpdate(user.getId());
	}

	private UserStat getOrCreateUserStatForUpdate(Long userId) {
		return userStatRepository.findByUserIdForUpdate(userId)
			.orElseGet(() -> {
				User user = userRepository.getReferenceById(userId);
				return userStatRepository.save(UserStat.builder().user(user).build());
			});
	}
}
