package com.samdev.appointments.identity.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * The token's sign-in level is too low for the request. Answered with {@code 401} and the standard
 * {@code insufficient_user_authentication} error, so the Angular app can start the step-up to level 2.
 */
class InsufficientUserAuthenticationException extends AccessDeniedException {

	InsufficientUserAuthenticationException(String message) {
		super(message);
	}
}
