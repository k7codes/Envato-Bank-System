package com.envatobank.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.envatobank.api.common.error.ApiException;
import com.envatobank.api.common.error.ErrorCode;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED, "No authenticated user in context");
    }
}
