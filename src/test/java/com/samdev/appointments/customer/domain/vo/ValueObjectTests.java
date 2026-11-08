package com.samdev.appointments.customer.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.samdev.appointments.customer.IdentitySubject;

/**
 * Every validation rule of the value objects (spec section 3.2).
 */
class ValueObjectTests {

	@Test
	@DisplayName("CU-1 a name part is 1 to 80 characters after trimming")
	void personName() {

		var name = new PersonName("  Thandi ", " Nkosi");
		assertThat(name.givenName()).isEqualTo("Thandi");
		assertThat(name.displayName()).isEqualTo("Thandi Nkosi");

		assertThat(new PersonName("a".repeat(80), "b").givenName()).hasSize(80);
		assertThatIllegalArgumentException().isThrownBy(() -> new PersonName("a".repeat(81), "b"));
		assertThatIllegalArgumentException().isThrownBy(() -> new PersonName("   ", "b"));
		assertThatIllegalArgumentException().isThrownBy(() -> new PersonName("a", null));
	}

	@Test
	@DisplayName("CU-1 an email is valid, at most 254 characters and stored lowercase")
	void emailAddress() {

		assertThat(new EmailAddress(" Thandi.N@Example.TEST ").value()).isEqualTo("thandi.n@example.test");

		assertThat(new EmailAddress("a".repeat(240) + "@example.test").value()).hasSize(253);
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("a".repeat(242) + "@example.test"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("no-at-sign.test"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("two@@example.test"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("space in@example.test"));
	}

	@ParameterizedTest
	@CsvSource({
			"+27821234567, +27821234567",
			"082 123 4567, +27821234567",
			"(082) 123-4567, +27821234567",
			"0027 82 123 4567, +27821234567",
			"+44 20 7946 0958, +442079460958",
			"+1.212.555.0100, +12125550100" })
	@DisplayName("CU-1 phone input is normalised to E.164 before validation")
	void phoneNormalisation(String input, String expected) {
		assertThat(PhoneNumber.parse(input, "27").value()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "+1234567", "+1234567890123456", "27821234567", "+27 82 abc 4567", "" })
	@DisplayName("CU-1 a phone number is + followed by 8 to 15 digits")
	void invalidPhoneNumbers(String input) {
		assertThatIllegalArgumentException().isThrownBy(() -> PhoneNumber.parse(input, "27"));
	}

	@Test
	@DisplayName("CU-5 the language of contact preferences is a lowercase language code")
	void contactPreferences() {

		assertThat(new ContactPreferences(true, false, "zu").language()).isEqualTo("zu");
		assertThatIllegalArgumentException().isThrownBy(() -> new ContactPreferences(true, false, "EN"));
		assertThatIllegalArgumentException().isThrownBy(() -> new ContactPreferences(true, false, "english"));
		assertThatIllegalArgumentException().isThrownBy(() -> new ContactPreferences(true, false, null));
	}

	@Test
	@DisplayName("CU-2 an identity subject is a non-blank string of at most 255 characters")
	void identitySubject() {

		assertThat(new IdentitySubject("a".repeat(255)).value()).hasSize(255);
		assertThatIllegalArgumentException().isThrownBy(() -> new IdentitySubject("a".repeat(256)));
		assertThatIllegalArgumentException().isThrownBy(() -> new IdentitySubject(" "));
		assertThatIllegalArgumentException().isThrownBy(() -> new IdentitySubject(null));
	}
}
