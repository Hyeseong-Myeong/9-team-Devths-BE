package com.ktb3.devths.board.service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.ktb3.devths.board.dto.response.PostDetailResponse;
import com.ktb3.devths.global.config.CacheConfig;
import com.ktb3.devths.global.config.properties.AppCacheProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostDetailCacheService {

	private static final String KEY_PREFIX = "v1:post:";
	private static final String VIEWER_SEGMENT = ":viewer:";
	private static final String INDEX_PREFIX = "v1:cache-index:post:";
	private static final Duration INDEX_TTL_BUFFER = Duration.ofSeconds(30);

	private final CacheManager cacheManager;
	private final StringRedisTemplate stringRedisTemplate;
	private final AppCacheProperties appCacheProperties;

	public Optional<PostDetailResponse> get(Long postId, Long userId) {
		if (!isEnabled()) {
			return Optional.empty();
		}

		try {
			Cache.ValueWrapper wrapper = getCache().get(buildCacheKey(postId, userId));
			if (wrapper == null) {
				return Optional.empty();
			}

			Object value = wrapper.get();
			if (value instanceof PostDetailResponse response) {
				return Optional.of(response);
			}

			return Optional.empty();
		} catch (Exception e) {
			log.warn("PostDetail 캐시 조회 실패: postId={}, userId={}", postId, userId, e);
			return Optional.empty();
		}
	}

	public void put(Long postId, Long userId, PostDetailResponse response) {
		if (!isEnabled()) {
			return;
		}

		String cacheKey = buildCacheKey(postId, userId);
		try {
			getCache().put(cacheKey, response);
			registerIndex(postId, cacheKey);
		} catch (Exception e) {
			log.warn("PostDetail 캐시 저장 실패: postId={}, userId={}", postId, userId, e);
		}
	}

	public void evictByPostId(Long postId) {
		if (!isEnabled()) {
			return;
		}

		String indexKey = buildIndexKey(postId);
		try {
			Set<String> cacheKeys = stringRedisTemplate.opsForSet().members(indexKey);
			if (cacheKeys != null) {
				Cache cache = getCache();
				for (String cacheKey : cacheKeys) {
					cache.evict(cacheKey);
				}
			}
			stringRedisTemplate.delete(indexKey);
		} catch (Exception e) {
			log.warn("PostDetail 캐시 무효화 실패: postId={}", postId, e);
		}
	}

	private void registerIndex(Long postId, String cacheKey) {
		String indexKey = buildIndexKey(postId);
		stringRedisTemplate.opsForSet().add(indexKey, cacheKey);
		Duration ttl = appCacheProperties.getPostDetail().getTtl().plus(INDEX_TTL_BUFFER);
		stringRedisTemplate.expire(indexKey, ttl);
	}

	private boolean isEnabled() {
		return appCacheProperties.isEnabled() && appCacheProperties.getPostDetail().isEnabled();
	}

	private Cache getCache() {
		Cache cache = cacheManager.getCache(CacheConfig.POST_DETAIL_CACHE);
		if (cache == null) {
			throw new IllegalStateException("postDetail cache is not configured");
		}
		return cache;
	}

	private String buildCacheKey(Long postId, Long userId) {
		return KEY_PREFIX + postId + VIEWER_SEGMENT + userId;
	}

	private String buildIndexKey(Long postId) {
		return INDEX_PREFIX + postId;
	}
}
