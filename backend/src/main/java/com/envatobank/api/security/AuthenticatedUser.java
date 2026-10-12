package com.envatobank.api.security;

import java.util.UUID;

/** Lightweight authenticated principal derived from a validated access token. */
public record AuthenticatedUser(UUID id, String email, String role) {
}
