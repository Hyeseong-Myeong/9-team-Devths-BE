package com.ktb3.devths.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ktb3.devths.global.response.ApiResponse;
import com.ktb3.devths.global.response.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AuthorizationDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthorizationDeniedException(
		AuthorizationDeniedException ex,
		HttpServletResponse response
	) {
		// 응답이 이미 커밋된 경우 (SSE 등) 로깅만 수행
		if (response.isCommitted()) {
			log.error("응답 커밋 후 권한 예외 발생 (SSE 스트리밍): {}", ex.getMessage());
			return null;
		}

		log.warn("권한 거부: {}", ex.getMessage());
		return ResponseEntity
			.status(HttpStatus.FORBIDDEN)
			.body(ApiResponse.error(ErrorCode.ACCESS_DENIED));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException ex
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorCode.INVALID_REQUEST));
	}

	@ExceptionHandler({
		ConstraintViolationException.class,
		BindException.class
	})
	public ResponseEntity<ApiResponse<Void>> handleBadRequestException(Exception ex) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorCode.INVALID_REQUEST));
	}

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
		log.error("CustomException: {}", ex.getMessage());
		ErrorCode errorCode = ex.getErrorCode();
		return ResponseEntity
			.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
		log.error("Unhandled Exception: ", ex);
		return ResponseEntity
			.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
			.body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
	}
}
