package com.ktb3.devths.user.dto.response;

import java.util.List;

public record FollowingListResponse(
	List<FollowerSummaryResponse> followings,
	boolean hasNext,
	Long lastId
) {
}
