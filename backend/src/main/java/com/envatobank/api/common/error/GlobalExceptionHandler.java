package com.envatobank.api.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        return ResponseEntity.status(code.status()).body(body(code, ex.getMessage(), request, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(body(ErrorCode.VALIDATION_ERROR, "Validation failed", request, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(cv -> fieldErrors.put(cv.getPropertyPath().toString(), cv.getMessage()));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(body(ErrorCode.VALIDATION_ERROR, "Validation failed", request, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(body(ErrorCode.VALIDATION_ERROR, "Malformed request body", request, null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.status())
                .body(body(ErrorCode.UNAUTHENTICATED, "Authentication required", request, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(body(ErrorCode.FORBIDDEN, "Access denied", request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(body(ErrorCode.INTERNAL_ERROR, "Unexpected error", request, null));
    }

    private ApiError body(ErrorCode code, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), code.status().value(), code.code(), message,
                request.getRequestURI(), fieldErrors);
    }
}
