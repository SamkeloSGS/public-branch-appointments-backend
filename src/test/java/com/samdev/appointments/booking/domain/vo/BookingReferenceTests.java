package com.samdev.appointments.booking.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.security.SecureRandom;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookingReferenceTests {

	@Test
	@DisplayName("BK-2 references are 8 characters without look-alikes, and practically unique")
	void references() {

		var random = new SecureRandom();
		var seen = new HashSet<String>();

		for (var i = 0; i < 10_000; i++) {
			var reference = BookingReference.random(random).value();
			assertThat(reference).hasSize(8).doesNotContainAnyWhitespaces().matches("[^01OIL]+");
			seen.add(reference);
		}

		assertThat(seen).hasSize(10_000);
	}

	@Test
	@DisplayName("BK-16 references typed by people are normalised; anything else is rejected")
	void parse() {

		assertThat(BookingReference.parse(" abcd2345 ").value()).isEqualTo("ABCD2345");
		assertThatIllegalArgumentException().isThrownBy(() -> BookingReference.parse("ABCD0123"));
		assertThatIllegalArgumentException().isThrownBy(() -> BookingReference.parse("ABC"));
		assertThatIllegalArgumentException().isThrownBy(() -> BookingReference.parse(null));
	}
}
