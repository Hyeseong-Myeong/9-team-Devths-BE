package com.ktb3.devths.user.dto.response;

import java.util.List;

public record FollowerListResponse(
	List<FollowerSummaryResponse> followers,
	boolean hasNext,
	Long lastId
) {
}
