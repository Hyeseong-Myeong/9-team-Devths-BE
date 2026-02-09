package com.ktb3.devths.board.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ktb3.devths.board.dto.request.CommentCreateRequest;
import com.ktb3.devths.board.dto.request.CommentUpdateRequest;
import com.ktb3.devths.board.dto.response.CommentCreateResponse;
import com.ktb3.devths.board.dto.response.CommentListResponse;
import com.ktb3.devths.board.dto.response.CommentUpdateResponse;
import com.ktb3.devths.board.service.CommentService;
import com.ktb3.devths.global.response.ApiResponse;
import com.ktb3.devths.global.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201")
	@PostMapping
	public ResponseEntity<ApiResponse<CommentCreateResponse>> createComment(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId,
		@Valid @RequestBody CommentCreateRequest request
	) {
		CommentCreateResponse response = commentService.createComment(
			userPrincipal.getUserId(), postId, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(
			ApiResponse.success("댓글이 성공적으로 등록되었습니다.", response)
		);
	}

	@PutMapping("/{commentId}")
	public ResponseEntity<ApiResponse<CommentUpdateResponse>> updateComment(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId,
		@PathVariable Long commentId,
		@Valid @RequestBody CommentUpdateRequest request
	) {
		CommentUpdateResponse response = commentService.updateComment(
			userPrincipal.getUserId(), postId, commentId, request);

		return ResponseEntity.ok(
			ApiResponse.success("댓글이 성공적으로 수정되었습니다.", response)
		);
	}

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204")
	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> deleteComment(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId,
		@PathVariable Long commentId
	) {
		commentService.deleteComment(userPrincipal.getUserId(), postId, commentId);
		return ResponseEntity.noContent().build();
	}

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
