package com.ktb3.devths.global.ratelimit.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.ktb3.devths.global.ratelimit.config.properties.RateLimitProperties;
import com.ktb3.devths.global.ratelimit.domain.constant.ApiType;
import com.ktb3.devths.global.ratelimit.exception.RateLimitExceededException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "ratelimit.backend", havingValue = "redis")
@RequiredArgsConstructor
public class RedisRateLimitService implements RateLimitService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
	private static final Long TOKEN_EXCEEDED = -1L;
	private static final DefaultRedisScript<Long> CONSUME_SCRIPT = buildConsumeScript();

	private final StringRedisTemplate redisTemplate;
	private final RateLimitProperties properties;

	@Override
	public void consumeToken(Long userId, ApiType apiType) {
		if (!isEnabled(apiType)) {
			return;
		}

		String key = buildKey(userId, apiType);
		int capacity = getCapacity(apiType);
		long ttlSeconds = getTtlUntilTomorrow();

		Long result = redisTemplate.execute(
			CONSUME_SCRIPT,
			List.of(key),
			String.valueOf(capacity),
			String.valueOf(ttlSeconds)
		);

		if (result == null || result.equals(TOKEN_EXCEEDED)) {
			log.warn("Rate limit 초과: userId={}, apiType={}", userId, apiType);
			throw new RateLimitExceededException(apiType, capacity);
		}

		log.debug("Rate limit 체크 통과: userId={}, apiType={}", userId, apiType);
	}

	@Override
	public int getConsumedCount(Long userId, ApiType apiType) {
		String value = redisTemplate.opsForValue().get(buildKey(userId, apiType));
		if (value == null) {
			return 0;
		}

		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	public int getRemainingTokens(Long userId, ApiType apiType) {
		int capacity = getCapacity(apiType);
		int consumed = getConsumedCount(userId, apiType);
		return Math.max(0, capacity - consumed);
	}

	@Override
	public void refillTokens(Long userId, ApiType apiType) {
		redisTemplate.delete(buildKey(userId, apiType));
		log.info("토큰 리필 완료: userId={}, apiType={}", userId, apiType);
	}

	private String buildKey(Long userId, ApiType apiType) {
		String dateKey = LocalDate.now().format(DATE_FORMATTER);
		return String.format(
			"%s:%s:%s:%d",
			properties.getRedis().getKeyPrefix(),
			dateKey,
			apiType.getKey(),
			userId
		);
	}

	private long getTtlUntilTomorrow() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
		long ttl = Duration.between(now, tomorrow).getSeconds();
		return Math.max(1L, ttl);
	}

	private int getCapacity(ApiType apiType) {
		return switch (apiType) {
			case GOOGLE_CALENDAR -> properties.getGoogleCalendar().getBucketCapacity();
			case GOOGLE_TASKS -> properties.getGoogleTasks().getBucketCapacity();
			case FASTAPI_ANALYSIS -> properties.getFastapi().getBucketCapacity();
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
			case GOOGLE_OAUTH -> properties.getGoogleOauth().isEnabled();
			case AUTH_TOKEN -> properties.getAuthToken().isEnabled();
			case FILE_PRESIGNED -> properties.getFilePresigned().isEnabled();
			case FILE_ATTACHMENT -> properties.getFileAttachment().isEnabled();
			case BOARD_WRITE -> properties.getBoardWrite().isEnabled();
			case SOCIAL_ACTION -> properties.getSocialAction().isEnabled();
		};
	}

	private static DefaultRedisScript<Long> buildConsumeScript() {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setResultType(Long.class);
		script.setScriptText("""
			local key = KEYS[1]
			local capacity = tonumber(ARGV[1])
			local ttl = tonumber(ARGV[2])
			local current = redis.call('GET', key)
			if not current then
				redis.call('SET', key, 1, 'EX', ttl)
				return 1
			end
			current = tonumber(current)
			if current >= capacity then
				return -1
			end
			local updated = redis.call('INCR', key)
			if updated == 1 then
				redis.call('EXPIRE', key, ttl)
			end
			return updated
			""");
		return script;
	}
}
