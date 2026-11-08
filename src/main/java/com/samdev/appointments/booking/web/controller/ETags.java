package com.samdev.appointments.booking.web.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aggregate versions as entity tags. Responses for single appointments carry {@code ETag: "<version>"}; actions must
 * send it back in {@code If-Match}.
 */
final class ETags {

	private ETags() {}

	static String of(long version) {
		return "\"" + version + "\"";
	}

	/**
	 * Parses the expected version from an {@code If-Match} header.
	 *
	 * @return the version, or {@code null} for {@code If-Match: *}
	 * @throws ResponseStatusException {@code 428} if the header is missing, {@code 412} if it names no version of
	 *     this resource
	 */
	static @Nullable Long expectedVersion(@Nullable String ifMatch) {

		if (ifMatch == null || ifMatch.isBlank()) {
			throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
					"This action requires an If-Match header with the appointment's ETag");
		}

		var value = ifMatch.strip();

		if (value.equals("*")) {
			return null;
		}
		if (value.startsWith("W/")) {
			value = value.substring(2);
		}

		try {
			return Long.valueOf(value.replace("\"", ""));
		} catch (NumberFormatException e) {
			throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "If-Match does not match any version");
		}
	}
}
