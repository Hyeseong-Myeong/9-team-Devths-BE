package com.ktb3.devths.user.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.user.domain.entity.Follow;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.domain.entity.UserStat;
import com.ktb3.devths.user.dto.response.FollowResponse;
import com.ktb3.devths.user.repository.FollowRepository;
import com.ktb3.devths.user.repository.UserRepository;
import com.ktb3.devths.user.repository.UserStatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

	private final UserRepository userRepository;
	private final FollowRepository followRepository;
	private final UserStatRepository userStatRepository;

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
