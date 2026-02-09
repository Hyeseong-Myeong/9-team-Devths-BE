package com.ktb3.devths.user.dto.response;

import java.util.List;

public record UserProfileResponse(
	UserInfo user,
	UserSignupResponse.ProfileImage profileImage,
	List<String> interests,
	boolean isFollowing
) {
	public record UserInfo(
		Long id,
		String nickname
	) {
	}

	public static UserProfileResponse of(
		Long userId,
		String nickname,
		UserSignupResponse.ProfileImage profileImage,
		List<String> interests,
		boolean isFollowing
	) {
		return new UserProfileResponse(
			new UserInfo(userId, nickname),
			profileImage,
			interests,
			isFollowing
		);
	}
}
