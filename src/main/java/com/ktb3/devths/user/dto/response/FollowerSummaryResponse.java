package com.ktb3.devths.user.dto.response;

public record FollowerSummaryResponse(
	Long id,
	Long userId,
	String nickname,
	String profileImage,
	boolean isFollowing
) {
}
