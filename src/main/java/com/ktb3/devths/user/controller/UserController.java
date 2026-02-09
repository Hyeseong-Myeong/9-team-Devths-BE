package com.ktb3.devths.user.controller;

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

import com.ktb3.devths.auth.util.CookieUtil;
import com.ktb3.devths.global.response.ApiResponse;
import com.ktb3.devths.global.security.UserPrincipal;
import com.ktb3.devths.user.dto.internal.UserSignupResult;
import com.ktb3.devths.user.dto.request.UserSignupRequest;
import com.ktb3.devths.user.dto.request.UserUpdateRequest;
import com.ktb3.devths.user.dto.response.MyPostListResponse;
import com.ktb3.devths.user.dto.response.UserMeResponse;
import com.ktb3.devths.user.dto.response.UserProfileResponse;
import com.ktb3.devths.user.dto.response.UserSignupResponse;
import com.ktb3.devths.user.dto.response.UserUpdateResponse;
import com.ktb3.devths.user.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
	private final UserService userService;

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201")
	@PostMapping
	public ResponseEntity<ApiResponse<UserSignupResponse>> signup(
		@Valid @RequestBody UserSignupRequest request,
		HttpServletResponse response
	) {
		UserSignupResult result = userService.signup(request);

		response.setHeader("Authorization", "Bearer " + result.tokenPair().accessToken());
		response.addCookie(CookieUtil.createRefreshTokenCookie(result.tokenPair().refreshToken()));

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success("회원가입에 성공하였습니다.", result.response()));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserMeResponse>> getMyInfo(
		@AuthenticationPrincipal UserPrincipal userPrincipal
	) {
		UserMeResponse response = userService.getMyInfo(userPrincipal.getUserId());

		return ResponseEntity.ok(
			ApiResponse.success("내 정보 조회에 성공하였습니다.", response)
		);
	}

	@PutMapping("/me")
	public ResponseEntity<ApiResponse<UserUpdateResponse>> updateMyInfo(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@Valid @RequestBody UserUpdateRequest request
	) {
		UserUpdateResponse response = userService.updateMyInfo(userPrincipal.getUserId(), request);

		return ResponseEntity.ok(
			ApiResponse.success("내 정보가 성공적으로 수정되었습니다.", response)
		);
	}

	@GetMapping("/me/posts")
	public ResponseEntity<ApiResponse<MyPostListResponse>> getMyPosts(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) Long lastId
	) {
		MyPostListResponse response = userService.getMyPosts(userPrincipal.getUserId(), size, lastId);

		return ResponseEntity.ok(
			ApiResponse.success("내가 작성한 게시글 목록을 성공적으로 조회하였습니다.", response)
		);
	}

	@GetMapping("/{userId}")
	public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		@PathVariable Long userId
	) {
		UserProfileResponse response = userService.getUserProfile(userPrincipal.getUserId(), userId);

		return ResponseEntity.ok(
			ApiResponse.success("회원의 프로필을 성공적으로 조회하였습니다.", response)
		);
	}

	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204")
	@DeleteMapping
	public ResponseEntity<Void> withdraw(
		@AuthenticationPrincipal UserPrincipal userPrincipal,
		HttpServletResponse response
	) {
		userService.withdraw(userPrincipal.getUserId());

		response.addCookie(CookieUtil.clearRefreshTokenCookie());

		return ResponseEntity.noContent().build();
	}
}
