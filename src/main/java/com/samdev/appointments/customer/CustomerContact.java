package com.samdev.appointments.customer;

import org.jspecify.annotations.Nullable;

/**
 * How to reach a customer right now. Read at send time and never stored by the caller.
 *
 * @param email present only while the email channel is enabled
 * @param phone E.164, present only while the SMS channel is enabled
 */
public record CustomerContact(CustomerId customerId, String displayName, String language, @Nullable String email,
		@Nullable String phone) {}
