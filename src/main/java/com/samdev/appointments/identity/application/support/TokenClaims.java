package com.samdev.appointments.identity.application.support;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.Subject;

/**
 * What the application uses from a validated token. Role claims are deliberately absent: they are never trusted.
 *
 * @param level the sign-in level from the {@code acr} claim: 1 for password, 2 for password and one-time code
 */
public record TokenClaims(Subject subject, @Nullable String email, boolean emailVerified, int level) {

	public boolean secondFactor() {
		return level >= 2;
	}
}
