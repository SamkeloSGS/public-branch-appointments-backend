package com.samdev.appointments.customer.infrastructure.config;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.samdev.appointments.customer.application.port.CustomerSettings;

/**
 * @param languages supported language codes, the default first
 * @param defaultCountryCode digits added to local phone numbers that start with {@code 0}
 */
@ConfigurationProperties("appointments.customer")
public record CustomerProperties(@Nullable List<String> languages, @Nullable String defaultCountryCode)
		implements CustomerSettings {

	public CustomerProperties {
		languages = languages == null || languages.isEmpty() ? List.of("en") : List.copyOf(languages);
		defaultCountryCode = defaultCountryCode == null || defaultCountryCode.isBlank() ? "27" : defaultCountryCode;

		if (!defaultCountryCode.matches("\\d{1,3}")) {
			throw new IllegalArgumentException("appointments.customer.default-country-code must be 1 to 3 digits");
		}
	}
}
