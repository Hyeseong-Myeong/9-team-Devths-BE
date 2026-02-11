package com.ktb3.devths.global.ratelimit.interceptor;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ktb3.devths.global.ratelimit.domain.constant.ApiType;
import com.ktb3.devths.global.ratelimit.service.RateLimitService;
import com.ktb3.devths.global.security.UserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Long SYSTEM_USER_ID = -1L;

	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	private final RateLimitService rateLimitService;
	private final List<RateLimitRule> rules = List.of(
		RateLimitRule.of("POST", "/api/files/presigned/signup", ApiType.FILE_PRESIGNED),
		RateLimitRule.of("POST", "/api/files/presigned", ApiType.FILE_PRESIGNED),
		RateLimitRule.of("POST", "/api/files", ApiType.FILE_ATTACHMENT),
		RateLimitRule.of("DELETE", "/api/files/{fileId}", ApiType.FILE_ATTACHMENT),
		RateLimitRule.of("POST", "/api/posts", ApiType.BOARD_WRITE),
		RateLimitRule.of("PUT", "/api/posts/{postId}", ApiType.BOARD_WRITE),
		RateLimitRule.of("DELETE", "/api/posts/{postId}", ApiType.BOARD_WRITE),
		RateLimitRule.of("POST", "/api/posts/{postId}/likes", ApiType.BOARD_WRITE),
		RateLimitRule.of("DELETE", "/api/posts/{postId}/likes", ApiType.BOARD_WRITE),
		RateLimitRule.of("POST", "/api/posts/{postId}/comments", ApiType.BOARD_WRITE),
		RateLimitRule.of("PUT", "/api/posts/{postId}/comments/{commentId}", ApiType.BOARD_WRITE),
		RateLimitRule.of("DELETE", "/api/posts/{postId}/comments/{commentId}", ApiType.BOARD_WRITE),
		RateLimitRule.of("POST", "/api/users/{userId}/followers", ApiType.SOCIAL_ACTION),
		RateLimitRule.of("DELETE", "/api/users/{userId}/followers", ApiType.SOCIAL_ACTION),
		RateLimitRule.of("POST", "/api/auth/logout", ApiType.AUTH_TOKEN)
	);

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}

		String method = request.getMethod();
		String requestUri = request.getRequestURI();

		for (RateLimitRule rule : rules) {
			if (rule.matches(method, requestUri, pathMatcher)) {
				rateLimitService.consumeToken(resolveUserId(), rule.apiType());
				break;
			}
		}

		return true;
	}

	private Long resolveUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
			return principal.getUserId();
		}

		return SYSTEM_USER_ID;
	}

	private record RateLimitRule(String method, String pathPattern, ApiType apiType) {
		private static RateLimitRule of(String method, String pathPattern, ApiType apiType) {
			return new RateLimitRule(method, pathPattern, apiType);
		}

		private boolean matches(String requestMethod, String requestUri, AntPathMatcher pathMatcher) {
			return method.equalsIgnoreCase(requestMethod) && pathMatcher.match(pathPattern, requestUri);
		}
	}
}
