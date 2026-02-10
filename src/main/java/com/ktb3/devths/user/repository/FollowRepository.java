package com.ktb3.devths.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ktb3.devths.user.domain.entity.Follow;

public interface FollowRepository extends JpaRepository<Follow, Long> {
	boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);
}
