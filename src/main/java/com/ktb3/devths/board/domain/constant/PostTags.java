package com.ktb3.devths.board.domain.constant;

import lombok.Getter;

@Getter
public enum PostTags {
	RESUME("이력서"),
	PORTFOLIO("포트폴리오"),
	INTERVIEW("면접"),
	CODING_TEST("코딩테스트");

	private final String displayName;

	PostTags(String displayName) {
		this.displayName = displayName;
	}

	public static PostTags fromDisplayName(String displayName) {
		for (PostTags postTags : values()) {
			if (postTags.displayName.equals(displayName)) {
				return postTags;
			}
		}
		return null;
	}
}
