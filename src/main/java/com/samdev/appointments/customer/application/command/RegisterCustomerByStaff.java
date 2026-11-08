package com.samdev.appointments.customer.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.Subject;

/**
 * Branch staff create a customer with no login account.
 *
 * @param staff the staff member, for the access audit
 * @param language {@code null} for the default
 */
public record RegisterCustomerByStaff(Subject staff, String givenName, String familyName, @Nullable String email,
		@Nullable String phone, @Nullable String language) {}
