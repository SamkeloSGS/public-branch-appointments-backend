package com.samdev.appointments.identity;

import org.jspecify.annotations.Nullable;

/**
 * Who is making the current request. Anyone signed in without an {@code ACTIVE} staff record is a customer: no staff
 * role.
 *
 * @param email the verified email address, if the token has one
 * @param staffRole {@code null} for customers
 * @param staffMemberId {@code null} for customers
 * @param secondFactor whether the token was issued at level 2 (password and one-time code)
 */
public record CurrentUser(Subject subject, @Nullable String email, @Nullable StaffRole staffRole,
		@Nullable StaffMemberId staffMemberId, boolean secondFactor) {

	public static final String CUSTOMER = "CUSTOMER";

	public boolean isStaff() {
		return staffRole != null;
	}

	/**
	 * The role name: a {@link StaffRole} name, or {@value #CUSTOMER}.
	 */
	public String role() {
		return staffRole == null ? CUSTOMER : staffRole.name();
	}
}
