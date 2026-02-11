package com.ktb3.devths.auth.service;

import java.time.LocalDateTime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ktb3.devths.auth.dto.internal.TokenPair;
import com.ktb3.devths.global.config.properties.JwtProperties;
import com.ktb3.devths.global.exception.CustomException;
import com.ktb3.devths.global.ratelimit.domain.constant.ApiType;
import com.ktb3.devths.global.ratelimit.service.RateLimitService;
import com.ktb3.devths.global.response.ErrorCode;
import com.ktb3.devths.global.security.jwt.JwtTokenProvider;
import com.ktb3.devths.user.domain.entity.User;
import com.ktb3.devths.user.domain.entity.UserToken;
import com.ktb3.devths.user.repository.UserTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class JwtTokenService {
	private final JwtTokenProvider jwtTokenProvider;
	private final UserTokenRepository userTokenRepository;
	private final JwtProperties jwtProperties;
	private final RateLimitService rateLimitService;

	/**
	 * 서비스 Access Token / Refresh Token 발급 및 저장
	 *
	 * @param user 사용자 엔티티
	 * @return TokenPair (accessToken, refreshToken, refreshTokenExpiresAt)
	 */
	@Transactional
	public TokenPair issueTokenPair(User user) {
		// Access Token 발급
		String accessToken = jwtTokenProvider.generateAccessToken(
			user.getId(),
			user.getEmail(),
			user.getRole().name()
		);

		// Refresh Token 발급
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

		// Refresh Token 만료 시간 계산
		LocalDateTime refreshTokenExpiresAt = LocalDateTime.now()
			.plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000);

		// UserToken 저장
		UserToken userToken = UserToken.builder()
			.user(user)
			.refreshToken(refreshToken)
			.expiresAt(refreshTokenExpiresAt)
			.build();
		userTokenRepository.save(userToken);

		log.info("토큰 발급 완료: userId={}", user.getId());

		return new TokenPair(accessToken, refreshToken, refreshTokenExpiresAt);
	}

	/**
	 * Refresh Token 무효화 (로그아웃)
	 *
	 * @param userId 사용자 ID
	 */
	@Transactional
	public void invalidateRefreshToken(Long userId) {
		User user = User.builder().id(userId).build();
		userTokenRepository.deleteByUserId(user.getId());

		log.info("Refresh Token 무효화 완료: userId={}", userId);
	}

	/**
	 * Refresh Token으로 새 토큰 쌍 발급 (RTR 적용)
	 *
	 * @param refreshToken Refresh Token
	 * @return TokenPair (accessToken, refreshToken, refreshTokenExpiresAt)
	 * @throws CustomException REFRESH_TOKEN_REUSED, EXPIRED_REFRESH_TOKEN, WITHDRAWN_USER
	 */
	@Transactional
	public TokenPair refreshTokens(String refreshToken) {
		// 1. RT로 DB 조회 (RTR 검증)
		UserToken userToken = userTokenRepository.findByRefreshToken(refreshToken)
			.orElseThrow(() -> {
				log.warn("재사용된 Refresh Token 감지");
				return new CustomException(ErrorCode.REFRESH_TOKEN_REUSED);
			});

		rateLimitService.consumeToken(userToken.getUser().getId(), ApiType.AUTH_TOKEN);

		// 2. 만료 확인
		if (userToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			log.warn("만료된 Refresh Token: userId={}", userToken.getUser().getId());
			userTokenRepository.delete(userToken);
			throw new CustomException(ErrorCode.EXPIRED_REFRESH_TOKEN);
		}

		// 3. User 조회 및 탈퇴 확인
		User user = userToken.getUser();
		if (user.isWithdraw()) {
			log.warn("탈퇴한 회원의 토큰 재발급 시도: userId={}", user.getId());
			userTokenRepository.delete(userToken);
			throw new CustomException(ErrorCode.WITHDRAWN_USER);
		}

		// 4. 기존 RT 삭제 (RTR 적용)
		userTokenRepository.delete(userToken);

		// 5. 새 토큰 쌍 발급
		TokenPair newTokenPair = issueTokenPair(user);

		log.info("토큰 재발급 완료: userId={}", user.getId());

		return newTokenPair;
	}
}
