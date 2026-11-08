package com.samdev.appointments.customer.domain.vo;

import java.util.regex.Pattern;

import jakarta.persistence.Column;

import org.jmolecules.ddd.types.ValueObject;

/**
 * Which channels are enabled, and the preferred language. Whether the language is supported is configuration and
 * checked by the application; whether a channel may be enabled depends on the contact details and is checked by the
 * aggregate. Embedded in the customer row.
 */
public record ContactPreferences(boolean emailEnabled, boolean smsEnabled,
		@Column(name = "preferred_language") String language) implements ValueObject {

	private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");

	public ContactPreferences {
		if (language == null || !LANGUAGE.matcher(language).matches()) {
			throw new IllegalArgumentException("Language must be a two or three letter lowercase code");
		}
	}
}
