package com.samdev.appointments.customer.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.identity.Subject;

/**
 * Replaces name, email and phone. An empty email or phone removes it.
 *
 * @param staff the staff member making the change, {@code null} when customers change their own details
 * @param expectedVersion {@code null} to skip the version check
 */
public record UpdateContactDetails(@Nullable Subject staff, CustomerId customerId, @Nullable Long expectedVersion,
		String givenName, String familyName, @Nullable String email, @Nullable String phone) {}
