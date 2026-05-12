package com.medora.common.exception;

import com.medora.common.api.ApiErrorResponse;
import com.medora.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("COMMON_INVALID_PARAM", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(
                        "COMMON_INVALID_PARAM",
                        "request validation failed",
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(fieldError -> new com.medora.common.api.ErrorDetail(
                                        fieldError.getField(),
                                        fieldError.getDefaultMessage()
                                ))
                                .toList()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        String requestId = com.medora.common.web.RequestMetadata.currentRequestId();
        String requestSummary = request == null ? "unknown-request" : request.getMethod() + " " + request.getRequestURI();
        log.error("Unhandled exception, requestId={}, request={}", requestId, requestSummary, ex);
        return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(
                        "COMMON_INTERNAL_ERROR",
                        "internal error",
                        null,
                        requestId,
                        java.time.OffsetDateTime.now()
                ));
    }
}
