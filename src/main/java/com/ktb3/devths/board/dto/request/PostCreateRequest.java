package com.ktb3.devths.board.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
	@NotBlank(message = "제목은 필수입니다")
	@Size(min = 1, max = 50, message = "제목은 최대 50자까지 입력할 수 있습니다.")
	String title,

	@NotBlank(message = "내용은 필수입니다")
	String content,

	List<String> tags,

	List<Long> fileIds
) {
}
