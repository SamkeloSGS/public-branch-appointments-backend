package com.samdev.appointments.customer.web.dto.request;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

/**
 * Registration by the customer or by staff. At least one of email and phone is required; customers registering
 * themselves without an email get the verified email of their sign-in.
 *
 * @param phone E.164, or a local number such as {@code 082 123 4567}
 * @param language {@code null} for the default
 */
public record CustomerRequest(@NotBlank String givenName, @NotBlank String familyName, @Nullable String email,
		@Nullable String phone, @Nullable String language) {}
