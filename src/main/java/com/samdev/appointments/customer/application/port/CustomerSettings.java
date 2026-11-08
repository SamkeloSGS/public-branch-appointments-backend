package com.samdev.appointments.customer.application.port;

import java.util.List;

/**
 * Configuration the rules depend on: the supported languages and how local phone numbers are read.
 */
public interface CustomerSettings {

	/**
	 * Lowercase language codes; the first is the default.
	 */
	List<String> languages();

	/**
	 * Digits only, added to local phone numbers that start with {@code 0}.
	 */
	String defaultCountryCode();

	default String defaultLanguage() {
		return languages().getFirst();
	}

	/**
	 * The language, or the default for {@code null}.
	 *
	 * @throws IllegalArgumentException for an unsupported language, mapped to {@code 400}
	 */
	default String supportedLanguage(String language) {

		if (language == null || language.isBlank()) {
			return defaultLanguage();
		}
		if (!languages().contains(language)) {
			throw new IllegalArgumentException("Language must be one of " + languages());
		}

		return language;
	}
}
