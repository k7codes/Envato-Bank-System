package com.envatobank.api.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "validation_error"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "unauthenticated"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid_credentials"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "account_locked"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not_found"),
    CONFLICT(HttpStatus.CONFLICT, "conflict"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "idempotency_conflict"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_funds"),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "limit_exceeded"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "currency_mismatch"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "account_not_active"),
    CUSTOMER_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "customer_not_active"),
    UNSUPPORTED_CURRENCY(HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_currency"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error");

    private final HttpStatus status;
    private final String code;

    ErrorCode(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
