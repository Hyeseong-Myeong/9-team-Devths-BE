package com.ktb3.devths.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record PrivateChatRoomCreateRequest(
	@NotNull(message = "상대 회원 ID는 필수입니다")
	Long userId
) {
}
