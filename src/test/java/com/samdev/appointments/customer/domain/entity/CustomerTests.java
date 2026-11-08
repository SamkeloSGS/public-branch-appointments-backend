package com.samdev.appointments.customer.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.samdev.appointments.customer.ContactPreferencesChanged;
import com.samdev.appointments.customer.CustomerContactDetailsChanged;
import com.samdev.appointments.customer.CustomerDeactivated;
import com.samdev.appointments.customer.CustomerErased;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.CustomerIdentityLinked;
import com.samdev.appointments.customer.CustomerReactivated;
import com.samdev.appointments.customer.CustomerRegistered;
import com.samdev.appointments.customer.IdentitySubject;
import com.samdev.appointments.customer.domain.event.LoginAccountErasureRequested;
import com.samdev.appointments.customer.domain.exception.CustomerRuleViolation;
import com.samdev.appointments.customer.domain.exception.RuleCode;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;
import com.samdev.appointments.customer.domain.vo.EmailAddress;
import com.samdev.appointments.customer.domain.vo.PersonName;
import com.samdev.appointments.customer.domain.vo.PhoneNumber;

/**
 * Invariants, defaults, transitions and registered events of the aggregate (spec section 3).
 */
class CustomerTests {

	static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");
	static final PersonName NAME = new PersonName("Thandi", "Nkosi");
	static final EmailAddress EMAIL = new EmailAddress("thandi@example.test");
	static final PhoneNumber PHONE = new PhoneNumber("+27821234567");

	@Test
	@DisplayName("CU-1 self-registration is ACTIVE, linked, with every channel that has a contact detail enabled")
	void registersWithDefaults() {

		var customer = Customer.register(CustomerId.random(), new IdentitySubject("sub-1"), NAME, EMAIL, PHONE, "en",
				NOW);

		assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
		assertThat(customer.getIdentitySubject()).isEqualTo(new IdentitySubject("sub-1"));
		assertThat(customer.getPreferences().emailEnabled()).isTrue();
		assertThat(customer.getPreferences().smsEnabled()).isTrue();
		assertThat(events(customer)).singleElement().isEqualTo(new CustomerRegistered(customer.getId(), true, NOW));
	}

	@Test
	@DisplayName("CU-3 a staff-created customer has no identity subject; only the present channel is enabled")
	void registersByStaff() {

		var customer = Customer.registerByStaff(CustomerId.random(), NAME, null, PHONE, "zu", NOW);

		assertThat(customer.getIdentitySubject()).isNull();
		assertThat(customer.getPreferences().emailEnabled()).isFalse();
		assertThat(customer.getPreferences().smsEnabled()).isTrue();
		assertThat(customer.getPreferences().language()).isEqualTo("zu");
		assertThat(events(customer)).singleElement().isEqualTo(new CustomerRegistered(customer.getId(), false, NOW));
	}

	@Test
	@DisplayName("CU-1 a customer needs at least one of email and phone")
	void needsAContactDetail() {
		assertRule(RuleCode.CONTACT_REQUIRED,
				() -> Customer.registerByStaff(CustomerId.random(), NAME, null, null, "en", NOW));
	}

	@Test
	@DisplayName("CU-4 removing the email disables its channel and enables SMS if nothing else is enabled")
	void removingEmailEnablesSms() {

		var customer = Customer.registerByStaff(CustomerId.random(), NAME, EMAIL, PHONE, "en", NOW);
		customer.changeContactPreferences(true, false, "en", NOW);

		customer.updateContactDetails(NAME, null, PHONE, NOW);

		assertThat(customer.getEmail()).isNull();
		assertThat(customer.getPreferences().emailEnabled()).isFalse();
		assertThat(customer.getPreferences().smsEnabled()).isTrue();
		assertThat(events(customer)).last().isInstanceOf(CustomerContactDetailsChanged.class);
	}

	@Test
	@DisplayName("CU-4 removing the phone disables SMS and keeps an enabled email channel")
	void removingPhoneDisablesSms() {

		var customer = Customer.registerByStaff(CustomerId.random(), NAME, EMAIL, PHONE, "en", NOW);

		customer.updateContactDetails(new PersonName("Thandiwe", "Nkosi"), EMAIL, null, NOW);

		assertThat(customer.getName().givenName()).isEqualTo("Thandiwe");
		assertThat(customer.getPreferences().emailEnabled()).isTrue();
		assertThat(customer.getPreferences().smsEnabled()).isFalse();
	}

	@Test
	@DisplayName("CU-4 an update that leaves neither email nor phone is rejected")
	void updateNeedsAContactDetail() {

		var customer = Customer.registerByStaff(CustomerId.random(), NAME, EMAIL, null, "en", NOW);

		assertRule(RuleCode.CONTACT_REQUIRED, () -> customer.updateContactDetails(NAME, null, null, NOW));
		assertThat(customer.getEmail()).isEqualTo(EMAIL);
	}

	@Test
	@DisplayName("CU-5 a channel needs its contact detail, and at least one channel stays enabled")
	void preferenceRules() {

		var customer = Customer.registerByStaff(CustomerId.random(), NAME, EMAIL, null, "en", NOW);

		assertRule(RuleCode.CHANNEL_WITHOUT_CONTACT, () -> customer.changeContactPreferences(true, true, "en", NOW));
		assertRule(RuleCode.NO_CHANNEL_ENABLED, () -> customer.changeContactPreferences(false, false, "en", NOW));

		customer.changeContactPreferences(true, false, "af", NOW);
		assertThat(customer.getPreferences().language()).isEqualTo("af");
		assertThat(events(customer)).last().isEqualTo(new ContactPreferencesChanged(customer.getId(), NOW));
	}

	@Test
	@DisplayName("CU-6 ACTIVE and DEACTIVATED switch both ways; a deactivated customer cannot change details")
	void deactivateAndReactivate() {

		var customer = Customer.registerByStaff(CustomerId.random(), NAME, EMAIL, null, "en", NOW);

		customer.deactivate(NOW);
		assertThat(customer.isActive()).isFalse();
		assertThat(events(customer)).last().isInstanceOf(CustomerDeactivated.class);

		assertRule(RuleCode.INVALID_STATE, () -> customer.deactivate(NOW));
		assertRule(RuleCode.INVALID_STATE, () -> customer.updateContactDetails(NAME, EMAIL, null, NOW));
		assertRule(RuleCode.INVALID_STATE, () -> customer.changeContactPreferences(true, false, "en", NOW));

		customer.reactivate(NOW);
		assertThat(customer.isActive()).isTrue();
		assertThat(events(customer)).last().isInstanceOf(CustomerReactivated.class);
		assertRule(RuleCode.INVALID_STATE, () -> customer.reactivate(NOW));
	}

	@Test
	@DisplayName("CU-7 erasure clears all personal data and the subject, disables both channels, and is final")
	void erase() {

		var customer = Customer.register(CustomerId.random(), new IdentitySubject("sub-2"), NAME, EMAIL, PHONE, "xh",
				NOW);
		customer.deactivate(NOW);

		customer.erase(NOW);

		assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ERASED);
		assertThat(customer.getName()).isNull();
		assertThat(customer.getEmail()).isNull();
		assertThat(customer.getPhone()).isNull();
		assertThat(customer.getIdentitySubject()).isNull();
		assertThat(customer.getPreferences().emailEnabled()).isFalse();
		assertThat(customer.getPreferences().smsEnabled()).isFalse();
		assertThat(events(customer)).last().isEqualTo(new CustomerErased(customer.getId(), NOW));
		assertThat(events(customer)).contains(new LoginAccountErasureRequested(new IdentitySubject("sub-2")));

		for (Runnable command : List.<Runnable> of(() -> customer.erase(NOW), () -> customer.reactivate(NOW),
				() -> customer.deactivate(NOW), () -> customer.updateContactDetails(NAME, EMAIL, null, NOW),
				() -> customer.changeContactPreferences(true, false, "en", NOW),
				() -> customer.linkIdentity(new IdentitySubject("sub-3"), NOW))) {
			assertRule(RuleCode.INVALID_STATE, command);
		}
	}

	@Test
	@DisplayName("CU-2 linking sets the subject once; a customer linked to someone else is not relinked")
	void linkIdentity() {

		var customer = Customer.registerByStaff(CustomerId.random(), NAME, EMAIL, null, "en", NOW);

		customer.linkIdentity(new IdentitySubject("sub-4"), NOW);
		assertThat(customer.getIdentitySubject()).isEqualTo(new IdentitySubject("sub-4"));
		assertThat(events(customer)).last().isEqualTo(new CustomerIdentityLinked(customer.getId(), NOW));

		assertRule(RuleCode.CONTACT_DETAILS_UNAVAILABLE, () -> customer.linkIdentity(new IdentitySubject("sub-5"), NOW));
	}

	private static void assertRule(RuleCode code, Runnable command) {
		assertThatExceptionOfType(CustomerRuleViolation.class).isThrownBy(command::run)
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(code))
				.satisfies(it -> assertThat(it.getMessage()).doesNotContain("@", "+27", "Thandi"));
	}

	@SuppressWarnings("unchecked")
	private static List<Object> events(Customer customer) {
		return List.copyOf((java.util.Collection<Object>) ReflectionTestUtils.invokeMethod(customer, "domainEvents"));
	}
}
