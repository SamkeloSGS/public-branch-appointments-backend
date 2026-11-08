package com.samdev.appointments.customer.web.dto.request;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

/**
 * Replaces name, email and phone. An empty email or phone removes it, which disables its channel.
 */
public record ContactDetailsRequest(@NotBlank String givenName, @NotBlank String familyName, @Nullable String email,
		@Nullable String phone) {}
