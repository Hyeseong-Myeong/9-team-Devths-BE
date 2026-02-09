package com.ktb3.devths.board.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ktb3.devths.board.dto.response.CommentListResponse;
import com.ktb3.devths.board.service.CommentService;
import com.ktb3.devths.global.response.ApiResponse;
import com.ktb3.devths.global.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;

	@GetMapping
	public ResponseEntity<ApiResponse<CommentListResponse>> getComments(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) Long lastId
	) {
		CommentListResponse response = commentService.getCommentList(postId, size, lastId);

		return ResponseEntity.ok(
			ApiResponse.success("댓글 목록을 성공적으로 조회하였습니다.", response)
		);
	}
}
