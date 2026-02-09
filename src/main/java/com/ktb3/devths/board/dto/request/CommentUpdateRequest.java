package com.ktb3.devths.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(
	@NotBlank(message = "댓글 내용은 필수입니다")
	@Size(min = 1, max = 500, message = "댓글은 최대 500자까지 작성 가능합니다")
	String content
) {
}
