package com.ktb3.devths.global.ratelimit.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ktb3.devths.global.ratelimit.config.properties.RateLimitProperties;
import com.ktb3.devths.global.ratelimit.domain.constant.ApiType;
import com.ktb3.devths.global.ratelimit.domain.entity.RateLimitKey;
import com.ktb3.devths.global.ratelimit.domain.entity.TokenBucket;
import com.ktb3.devths.global.ratelimit.exception.RateLimitExceededException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "ratelimit.backend", havingValue = "in-memory", matchIfMissing = true)
@RequiredArgsConstructor
public class InMemoryRateLimitService implements RateLimitService {

	private final RateLimitProperties properties;
	private final Map<RateLimitKey, TokenBucket> storage = new ConcurrentHashMap<>();

	@Override
	public void consumeToken(Long userId, ApiType apiType) {
		if (!isEnabled(apiType)) {
			return;
		}

		RateLimitKey key = RateLimitKey.of(userId, apiType);
		int capacity = getCapacity(apiType);

		TokenBucket bucket = storage.computeIfAbsent(key, k -> new TokenBucket());
		boolean success = bucket.tryConsumeToken(capacity);

		if (!success) {
			log.warn("Rate limit 초과: userId={}, apiType={}", userId, apiType);
			throw new RateLimitExceededException(apiType, capacity);
		}

		log.debug("Rate limit 체크 통과: userId={}, apiType={}", userId, apiType);
	}

	@Override
	public int getConsumedCount(Long userId, ApiType apiType) {
		RateLimitKey key = RateLimitKey.of(userId, apiType);
		TokenBucket bucket = storage.get(key);
		return bucket != null ? bucket.getConsumedCount() : 0;
	}

	@Override
	public int getRemainingTokens(Long userId, ApiType apiType) {
		int capacity = getCapacity(apiType);
		int consumed = getConsumedCount(userId, apiType);
		return Math.max(0, capacity - consumed);
	}

	@Override
	public void refillTokens(Long userId, ApiType apiType) {
		RateLimitKey key = RateLimitKey.of(userId, apiType);
		storage.remove(key);
		log.info("토큰 리필 완료: userId={}, apiType={}", userId, apiType);
	}

	/**
	 * 매일 자정(00:00) 어제 날짜의 키 삭제 (토큰 리필)
	 */
	@Scheduled(cron = "0 0 0 * * *")
	public void resetDailyCounters() {
		LocalDate today = LocalDate.now();
		int removedCount = 0;

		for (RateLimitKey key : storage.keySet()) {
			if (key.date().isBefore(today)) {
				storage.remove(key);
				removedCount++;
			}
		}

		log.info("토큰 버킷 자정 리필 완료: 제거된 키 개수={}", removedCount);
	}

	private int getCapacity(ApiType apiType) {
		return switch (apiType) {
			case GOOGLE_CALENDAR -> properties.getGoogleCalendar().getBucketCapacity();
			case GOOGLE_TASKS -> properties.getGoogleTasks().getBucketCapacity();
			case FASTAPI_ANALYSIS -> properties.getFastapi().getBucketCapacity();
			case FASTAPI_CHAT -> properties.getFastapiChat().getBucketCapacity();
			case FASTAPI_EVALUATION -> properties.getFastapiEvaluation().getBucketCapacity();
			case GOOGLE_OAUTH -> properties.getGoogleOauth().getBucketCapacity();
			case AUTH_TOKEN -> properties.getAuthToken().getBucketCapacity();
			case FILE_PRESIGNED -> properties.getFilePresigned().getBucketCapacity();
			case FILE_ATTACHMENT -> properties.getFileAttachment().getBucketCapacity();
			case BOARD_WRITE -> properties.getBoardWrite().getBucketCapacity();
			case SOCIAL_ACTION -> properties.getSocialAction().getBucketCapacity();
		};
	}

	private boolean isEnabled(ApiType apiType) {
		return switch (apiType) {
			case GOOGLE_CALENDAR -> properties.getGoogleCalendar().isEnabled();
			case GOOGLE_TASKS -> properties.getGoogleTasks().isEnabled();
			case FASTAPI_ANALYSIS -> properties.getFastapi().isEnabled();
			case FASTAPI_CHAT -> properties.getFastapiChat().isEnabled();
			case FASTAPI_EVALUATION -> properties.getFastapiEvaluation().isEnabled();
			case GOOGLE_OAUTH -> properties.getGoogleOauth().isEnabled();
			case AUTH_TOKEN -> properties.getAuthToken().isEnabled();
			case FILE_PRESIGNED -> properties.getFilePresigned().isEnabled();
			case FILE_ATTACHMENT -> properties.getFileAttachment().isEnabled();
			case BOARD_WRITE -> properties.getBoardWrite().isEnabled();
			case SOCIAL_ACTION -> properties.getSocialAction().isEnabled();
		};
	}
}
