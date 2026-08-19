package com.ktb.hackathon.team11.global.handler;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ApiResponse.onFailure(errorCode.name(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(fieldError -> fieldError.getDefaultMessage())
				.orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());

		return ResponseEntity
				.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
				.body(ApiResponse.onFailure(ErrorCode.INVALID_INPUT_VALUE.name(), message));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		return ResponseEntity
				.status(ErrorCode.INVALID_JSON_FORMAT.getStatus())
				.body(ApiResponse.onFailure(
						ErrorCode.INVALID_JSON_FORMAT.name(),
						ErrorCode.INVALID_JSON_FORMAT.getMessage()
				));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("Unhandled exception", exception);
		return ResponseEntity
				.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
				.body(ApiResponse.onFailure(
						ErrorCode.INTERNAL_SERVER_ERROR.name(),
						ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
				));
	}
}
