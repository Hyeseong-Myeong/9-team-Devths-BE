package com.ktb3.devths.chat.domain.constant;

import lombok.Getter;

@Getter
public enum ChatRoomTypes {
	PRIVATE("개인"),
	GROUP("단체");

	private final String displayName;

	ChatRoomTypes(String displayName) {
		this.displayName = displayName;
	}

	public static ChatRoomTypes fromDisplayName(String displayName) {
		for (ChatRoomTypes type : values()) {
			if (type.displayName.equalsIgnoreCase(displayName)) {
				return type;
			}
		}

		return null;
	}
}
