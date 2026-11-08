package com.samdev.appointments.identity.web.dto.response;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.CurrentUser;

/**
 * @param role a staff role or {@code CUSTOMER}
 * @param displayName only for staff
 * @param secondFactor whether the token is at level 2
 */
public record MeResponse(String role, @Nullable String displayName, boolean secondFactor) {

	public static MeResponse of(CurrentUser user, @Nullable String displayName) {
		return new MeResponse(user.role(), displayName, user.secondFactor());
	}
}
