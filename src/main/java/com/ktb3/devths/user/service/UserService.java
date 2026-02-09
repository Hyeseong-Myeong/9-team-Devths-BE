package com.ktb3.devths.user.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.auth.dto.internal.TokenPair;
import com.ktb3.devths.auth.service.JwtTokenService;
import com.ktb3.devths.auth.service.TokenEncryptionService;
import com.ktb3.devths.board.domain.entity.Comment;
import com.ktb3.devths.board.domain.entity.Post;
import com.ktb3.devths.board.repository.CommentRepository;
import com.ktb3.devths.board.repository.PostRepository;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.security.jwt.JwtTokenProvider;
import com.ktb3.devths.global.security.jwt.JwtTokenValidator;
import com.ktb3.devths.global.storage.domain.constant.RefType;
import com.ktb3.devths.global.storage.domain.entity.S3Attachment;
import com.ktb3.devths.global.storage.repository.S3AttachmentRepository;
import com.ktb3.devths.global.storage.service.S3StorageService;
import com.ktb3.devths.user.domain.constant.Interests;
import com.ktb3.devths.user.domain.constant.UserRoles;
import com.ktb3.devths.user.domain.entity.SocialAccount;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.domain.entity.UserInterest;
import com.ktb3.devths.user.dto.internal.UserSignupResult;
import com.ktb3.devths.user.dto.request.UserSignupRequest;
import com.ktb3.devths.user.dto.request.UserUpdateRequest;
import com.ktb3.devths.user.dto.response.MyCommentListResponse;
import com.ktb3.devths.user.dto.response.MyPostListResponse;
import com.ktb3.devths.user.dto.response.UserMeResponse;
import com.ktb3.devths.user.dto.response.UserProfileResponse;
import com.ktb3.devths.user.dto.response.UserSignupResponse;
import com.ktb3.devths.user.dto.response.UserUpdateResponse;
import com.ktb3.devths.user.repository.SocialAccountRepository;
import com.ktb3.devths.user.repository.UserInterestRepository;
import com.ktb3.devths.user.repository.UserRepository;
import com.ktb3.devths.user.repository.UserTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
	private static final String PROVIDER_GOOGLE = "GOOGLE";
	private static final int DEFAULT_MY_POST_PAGE_SIZE = 5;
	private static final int MAX_MY_POST_PAGE_SIZE = 100;
	private static final int DEFAULT_MY_COMMENT_PAGE_SIZE = 5;
	private static final int MAX_MY_COMMENT_PAGE_SIZE = 100;

	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final UserInterestRepository userInterestRepository;
	private final UserTokenRepository userTokenRepository;
	private final JwtTokenValidator jwtTokenValidator;
	private final JwtTokenProvider jwtTokenProvider;
	private final JwtTokenService jwtTokenService;
	private final TokenEncryptionService tokenEncryptionService;
	private final S3AttachmentRepository s3AttachmentRepository;
	private final S3StorageService s3StorageService;
	private final CommentRepository commentRepository;
	private final PostRepository postRepository;

	@Transactional
	public UserSignupResult signup(UserSignupRequest request) {
		jwtTokenValidator.validateTempToken(request.tempToken());

		String emailFromToken = jwtTokenProvider.getEmailFromToken(request.tempToken());
		if (!emailFromToken.equals(request.email())) {
			throw new CustomException(ErrorCode.INVALID_TEMP_TOKEN);
		}

		String provider = jwtTokenProvider.getProviderFromTempToken(request.tempToken());
		String googleSub = jwtTokenProvider.getGoogleSubFromTempToken(request.tempToken());
		String googleAccessToken = jwtTokenProvider.getGoogleAccessTokenFromTempToken(request.tempToken());
		String googleRefreshToken = jwtTokenProvider.getGoogleRefreshTokenFromTempToken(request.tempToken());
		int googleAccessTokenExpiresIn = jwtTokenProvider.getGoogleAccessTokenExpiresInFromTempToken(
			request.tempToken()
		);

		if (!PROVIDER_GOOGLE.equals(provider)) {
			throw new CustomException(ErrorCode.INVALID_TEMP_TOKEN);
		}

		if (userRepository.existsByEmail(request.email())) {
			throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
		}

		if (userRepository.existsByNickname(request.nickname())) {
			throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
		}

		if (socialAccountRepository.findByProviderAndProviderUserId(provider, googleSub).isPresent()) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		User user = userRepository.save(User.builder()
			.email(request.email())
			.nickname(request.nickname())
			.role(UserRoles.ROLE_USER)
			.isWithdraw(false)
			.build());

		String encryptedGoogleAccessToken = tokenEncryptionService.encrypt(googleAccessToken);
		// RT may be missing for first-time consent; store empty value to satisfy non-null column.
		String encryptedGoogleRefreshToken = googleRefreshToken == null
			? ""
			: tokenEncryptionService.encrypt(googleRefreshToken);
		LocalDateTime googleTokenExpiresAt = LocalDateTime.now().plusSeconds(googleAccessTokenExpiresIn);

		SocialAccount socialAccount = SocialAccount.builder()
			.user(user)
			.provider(provider)
			.providerUserId(googleSub)
			.accessToken(encryptedGoogleAccessToken)
			.refreshToken(encryptedGoogleRefreshToken)
			.expiresAt(googleTokenExpiresAt)
			.build();
		socialAccountRepository.save(socialAccount);

		List<UserInterest> interests = request.interests().stream()
			.distinct()
			.map(this::parseInterest)
			.map(interest -> UserInterest.builder().user(user).interest(interest).build())
			.collect(Collectors.toList());

		try {
			// Duplicate interests are handled by DB unique constraints for now.
			userInterestRepository.saveAll(interests);
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(ErrorCode.INVALID_INPUT);
		}

		// 프로필 사진 처리
		UserSignupResponse.ProfileImage profileImage = null;
		if (request.profileImageS3Key() != null && !request.profileImageS3Key().isBlank()) {
			S3Attachment attachment = S3Attachment.builder()
				.user(user)
				.originalName("profile.jpg")
				.s3Key(request.profileImageS3Key())
				.mimeType("image/jpeg")
				.category(null)
				.fileSize(0L)
				.refType(RefType.USER)
				.refId(user.getId())
				.sortOrder(0)
				.build();

			S3Attachment savedAttachment = s3AttachmentRepository.save(attachment);

			profileImage = new UserSignupResponse.ProfileImage(
				savedAttachment.getId(),
				s3StorageService.getPublicUrl(savedAttachment.getS3Key())
			);
		}

		TokenPair tokenPair = jwtTokenService.issueTokenPair(user);

		List<String> interestNames = interests.stream()
			.map(userInterest -> userInterest.getInterest().getDisplayName())
			.collect(Collectors.toList());

		UserSignupResponse response = UserSignupResponse.of(user, interestNames, profileImage);

		log.info("회원가입 성공: userId={}", user.getId());

		return new UserSignupResult(response, tokenPair);
	}

	@Transactional(readOnly = true)
	public UserMeResponse getMyInfo(Long userId) {
		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		List<Interests> interests = userInterestRepository.findInterestsByUserId(userId);

		List<String> interestNames = interests.stream()
			.map(Interests::getDisplayName)
			.collect(Collectors.toList());

		UserSignupResponse.ProfileImage profileImage = s3AttachmentRepository
			.findTopByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType.USER, userId)
			.map(attachment -> new UserSignupResponse.ProfileImage(
				attachment.getId(),
				s3StorageService.getPublicUrl(attachment.getS3Key())
			))
			.orElse(null);

		return UserMeResponse.of(user, interestNames, profileImage);
	}

	@Transactional
	public UserUpdateResponse updateMyInfo(Long userId, UserUpdateRequest request) {
		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		// 닉네임 변경 시 중복 체크
		if (!user.getNickname().equals(request.nickname())) {
			if (userRepository.existsByNickname(request.nickname())) {
				throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
			}
			user.updateNickname(request.nickname());
		}

		// interests가 전달된 경우 업데이트
		if (request.interests() != null) {
			// 기존 UserInterest 삭제
			userInterestRepository.deleteAllByUser_Id(userId);
			userInterestRepository.flush();

			// 빈 배열이 아닌 경우에만 새로운 관심사 추가
			if (!request.interests().isEmpty()) {
				List<UserInterest> newInterests = request.interests().stream()
					.distinct()
					.map(this::parseInterest)
					.map(interest -> UserInterest.builder().user(user).interest(interest).build())
					.collect(Collectors.toList());

				try {
					userInterestRepository.saveAll(newInterests);
				} catch (DataIntegrityViolationException e) {
					throw new CustomException(ErrorCode.INVALID_INPUT);
				}
			}
		}

		// 응답 생성
		List<Interests> interests = userInterestRepository.findInterestsByUserId(userId);
		List<String> interestNames = interests.stream()
			.map(Interests::getDisplayName)
			.collect(Collectors.toList());

		return UserUpdateResponse.of(user, interestNames);
	}

	@Transactional
	public void withdraw(Long userId) {
		User user = userRepository.findByIdAndIsWithdrawFalse(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		// 연관 데이터 삭제
		socialAccountRepository.deleteAllByUser_Id(userId);  // 재가입 허용
		userInterestRepository.deleteAllByUser_Id(userId);    // 관심사 삭제
		userTokenRepository.deleteByUserId(userId);           // Refresh Token 삭제

		user.withdraw();
	}

	@Transactional(readOnly = true)
	public MyPostListResponse getMyPosts(Long userId, Integer size, Long lastId) {
		int pageSize = (size == null || size <= 0)
			? DEFAULT_MY_POST_PAGE_SIZE
			: Math.min(size, MAX_MY_POST_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		List<Post> posts = (lastId == null)
			? postRepository.findMyPostsNotDeleted(userId, pageable)
			: postRepository.findMyPostsNotDeletedAfterCursor(userId, lastId, pageable);

		return MyPostListResponse.of(posts, pageSize);
	}

	@Transactional(readOnly = true)
	public MyCommentListResponse getMyComments(Long userId, Integer size, Long lastId) {
		int pageSize = (size == null || size <= 0)
			? DEFAULT_MY_COMMENT_PAGE_SIZE
			: Math.min(size, MAX_MY_COMMENT_PAGE_SIZE);
		Pageable pageable = PageRequest.of(0, pageSize + 1);

		List<Comment> comments = (lastId == null)
			? commentRepository.findMyCommentsNotDeleted(userId, pageable)
			: commentRepository.findMyCommentsNotDeletedAfterCursor(userId, lastId, pageable);

		return MyCommentListResponse.of(comments, pageSize);
	}

	@Transactional(readOnly = true)
	public UserProfileResponse getUserProfile(Long requesterId, Long targetUserId) { //requesterId -> 추후 isFollowing 계산 시 필요
		User user = userRepository.findByIdAndIsWithdrawFalse(targetUserId)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		List<String> interests = userInterestRepository.findInterestsByUserId(targetUserId).stream()
			.map(Interests::getDisplayName)
			.toList();

		UserSignupResponse.ProfileImage profileImage = s3AttachmentRepository
			.findTopByRefTypeAndRefIdAndIsDeletedFalseOrderByCreatedAtDesc(RefType.USER, targetUserId)
			.map(attachment -> new UserSignupResponse.ProfileImage(
				attachment.getId(),
				s3StorageService.getPublicUrl(attachment.getS3Key())
			))
			.orElse(null);

		return UserProfileResponse.of(
			user.getId(),
			user.getNickname(),
			profileImage,
			interests,
			false
		);
	}

	private Interests parseInterest(String value) {
		// 영문 대문자로 시도 (BACKEND, FRONTEND 등)
		try {
			return Interests.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			// 영문 실패 시 한글로 시도 (백엔드, 프론트엔드 등)
			Interests interest = Interests.fromDisplayName(value);
			if (interest != null) {
				return interest;
			}
			throw new CustomException(ErrorCode.INVALID_INPUT);
		}
	}
}
