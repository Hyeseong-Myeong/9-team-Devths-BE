package com.ktb3.devths.chat.domain.constant;

import lombok.Getter;

@Getter
public enum ChatRoomTags {
	RESUME("이력서"),
	PORTFOLIO("포트폴리오"),
	INTERVIEW("면접"),
	CODING_TEST("코딩테스트");

	private final String displayName;

	ChatRoomTags(String displayName) {
		this.displayName = displayName;
	}

	public static ChatRoomTags fromDisplayName(String displayName) {
		for (ChatRoomTags tags : values()) {
			if (tags.displayName.equals(displayName)) {
				return tags;
			}
		}
		return null;
	}
}
