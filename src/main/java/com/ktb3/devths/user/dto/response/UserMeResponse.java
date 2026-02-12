package com.ktb3.devths.user.dto.response;

import java.util.List;

import com.ktb3.devths.user.domain.entity.User;

public record UserMeResponse(
	String nickname,
	UserSignupResponse.ProfileImage profileImage,
	UserSignupResponse.UserStats stats,
	List<String> interests
) {
	public static UserMeResponse of(User user, List<String> interestNames, UserSignupResponse.ProfileImage profileImage,
		long followerCount, long followingCount) {
		return new UserMeResponse(
			user.getNickname(),
			profileImage,
			new UserSignupResponse.UserStats(followerCount, followingCount),
			interestNames
		);
	}
}
