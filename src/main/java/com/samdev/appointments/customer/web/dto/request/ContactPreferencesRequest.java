package com.samdev.appointments.customer.web.dto.request;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/**
 * @param language {@code null} keeps the current language
 */
public record ContactPreferencesRequest(@NotNull Boolean emailEnabled, @NotNull Boolean smsEnabled,
		@Nullable String language) {}
