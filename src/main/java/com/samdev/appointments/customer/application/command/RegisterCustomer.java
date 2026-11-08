package com.samdev.appointments.customer.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.IdentitySubject;

/**
 * A signed-in person creates their own profile, or is linked to a staff-created customer with their verified email.
 *
 * @param verifiedEmail the verified email from the token, if any; used when {@code email} is empty, and the only
 *     email ever used for linking
 * @param language {@code null} for the default
 */
public record RegisterCustomer(IdentitySubject subject, @Nullable String verifiedEmail, String givenName,
		String familyName, @Nullable String email, @Nullable String phone, @Nullable String language) {}
