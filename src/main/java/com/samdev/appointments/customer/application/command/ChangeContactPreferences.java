package com.samdev.appointments.customer.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.CustomerId;

/**
 * Customers only.
 *
 * @param language {@code null} keeps the current language
 */
public record ChangeContactPreferences(CustomerId customerId, @Nullable Long expectedVersion, boolean emailEnabled,
		boolean smsEnabled, @Nullable String language) {}
