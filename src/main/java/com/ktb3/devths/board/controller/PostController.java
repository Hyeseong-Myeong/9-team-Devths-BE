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

import com.ktb3.devths.board.dto.request.PostCreateRequest;
import com.ktb3.devths.board.dto.request.PostUpdateRequest;
import com.ktb3.devths.board.dto.response.PostCreateResponse;
import com.ktb3.devths.board.dto.response.PostDetailResponse;
import com.ktb3.devths.board.dto.response.PostLikeResponse;
import com.ktb3.devths.board.dto.response.PostListResponse;
import com.ktb3.devths.board.dto.response.PostUpdateResponse;
import com.ktb3.devths.board.service.PostService;
import com.ktb3.devths.global.response.ApiResponse;
import com.ktb3.devths.global.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201")
	@PostMapping
	public ResponseEntity<ApiResponse<PostCreateResponse>> createPost(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@Valid @RequestBody PostCreateRequest request
	) {
		PostCreateResponse response = postService.createPost(userPrincipal.getUserId(), request);

		return ResponseEntity.status(HttpStatus.CREATED).body(
			ApiResponse.success("게시글이 성공적으로 등록되었습니다.", response)
		);
	}

	@PutMapping("/{postId}")
	public ResponseEntity<ApiResponse<PostUpdateResponse>> updatePost(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId,
		@Valid @RequestBody PostUpdateRequest request
	) {
		PostUpdateResponse response = postService.updatePost(userPrincipal.getUserId(), postId, request);

		return ResponseEntity.ok(
			ApiResponse.success("게시글이 성공적으로 수정되었습니다.", response)
		);
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PostListResponse>> getPosts(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) Long lastId,
		@RequestParam(required = false) String tag
	) {
		PostListResponse response = postService.getPostList(size, lastId, tag);

		return ResponseEntity.ok(
			ApiResponse.success("게시글 목록을 성공적으로 조회하였습니다.", response)
		);
	}

	@GetMapping("/{postId}")
	public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId
	) {
		PostDetailResponse response = postService.getPostDetail(userPrincipal.getUserId(), postId);

		return ResponseEntity.ok(
			ApiResponse.success("게시글을 성공적으로 조회하였습니다.", response)
		);
	}

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204")
	@DeleteMapping("/{postId}")
	public ResponseEntity<Void> deletePost(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId
	) {
		postService.deletePost(userPrincipal.getUserId(), postId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{postId}/likes")
	public ResponseEntity<ApiResponse<PostLikeResponse>> likePost(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId
	) {
		PostLikeResponse response = postService.likePost(userPrincipal.getUserId(), postId);
		return ResponseEntity.ok(
			ApiResponse.success("해당 게시글에 좋아요를 눌렀습니다.", response)
		);
	}

	@DeleteMapping("/{postId}/likes")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204")
	public ResponseEntity<Void> unlikePost(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long postId
	) {
		postService.unlikePost(userPrincipal.getUserId(), postId);
		return ResponseEntity.noContent().build();
	}
}
