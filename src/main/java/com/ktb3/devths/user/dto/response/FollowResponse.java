package com.ktb3.devths.user.dto.response;

public record FollowResponse(
	Long targetUserId,
	Long followingCount
) {
	public static FollowResponse of(Long targetUserId, Long followingCount) {
		return new FollowResponse(targetUserId, followingCount);
	}
}
