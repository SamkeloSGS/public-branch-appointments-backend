package com.samdev.appointments.customer.domain.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AbstractAggregateRoot;

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
import com.samdev.appointments.customer.domain.vo.ContactPreferences;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;
import com.samdev.appointments.customer.domain.vo.EmailAddress;
import com.samdev.appointments.customer.domain.vo.PersonName;
import com.samdev.appointments.customer.domain.vo.PhoneNumber;

/**
 * A person who books or attends appointments. Linked to a login account by its identity subject, or created by
 * branch staff without one. Unless erased: at least one of email and phone is present, a channel is enabled only
 * with its contact detail, and at least one channel is enabled. Erasure clears all personal data and is final.
 */
@Table(schema = "customer", name = "customer")
public class Customer extends AbstractAggregateRoot<Customer> implements AggregateRoot<Customer, CustomerId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final CustomerId id;

	private @Nullable String identitySubject;

	/**
	 * Empty after erasure.
	 */
	private @Nullable PersonName name;

	/**
	 * Lowercase.
	 */
	private @Nullable String email;

	/**
	 * E.164.
	 */
	private @Nullable String phone;

	private ContactPreferences preferences;

	@Enumerated(EnumType.STRING)
	private CustomerStatus status;

	private final Instant createdAt;

	@Version
	private Long version;

	private Customer(CustomerId id, @Nullable IdentitySubject subject, PersonName name, @Nullable EmailAddress email,
			@Nullable PhoneNumber phone, String language, Instant now) {

		this.id = Objects.requireNonNull(id);
		this.identitySubject = subject == null ? null : subject.value();
		this.name = Objects.requireNonNull(name, "Name is required");
		this.email = email == null ? null : email.value();
		this.phone = phone == null ? null : phone.value();
		this.status = CustomerStatus.ACTIVE;
		this.createdAt = now;

		assertContactPresent();

		// Every channel with a contact detail starts enabled
		this.preferences = new ContactPreferences(email != null, phone != null, language);
	}

	/**
	 * A signed-in person creates their own profile, linked to their login account.
	 */
	public static Customer register(CustomerId id, IdentitySubject subject, PersonName name,
			@Nullable EmailAddress email, @Nullable PhoneNumber phone, String language, Instant now) {

		var customer = new Customer(id, Objects.requireNonNull(subject), name, email, phone, language, now);
		customer.registerEvent(new CustomerRegistered(id, true, now));
		return customer;
	}

	/**
	 * Branch staff create a customer for a walk-in or phone booking, with no login account.
	 */
	public static Customer registerByStaff(CustomerId id, PersonName name, @Nullable EmailAddress email,
			@Nullable PhoneNumber phone, String language, Instant now) {

		var customer = new Customer(id, null, name, email, phone, language, now);
		customer.registerEvent(new CustomerRegistered(id, false, now));
		return customer;
	}

	/**
	 * Replaces name and contact details. A removed detail disables its channel; if that leaves no channel enabled,
	 * the remaining one is enabled.
	 */
	public void updateContactDetails(PersonName name, @Nullable EmailAddress email, @Nullable PhoneNumber phone,
			Instant now) {

		assertActive("changed");

		if (email == null && phone == null) {
			throw new CustomerRuleViolation(RuleCode.CONTACT_REQUIRED, "At least one of email or phone is required");
		}

		var emailOn = preferences.emailEnabled() && email != null;
		var smsOn = preferences.smsEnabled() && phone != null;

		if (!emailOn && !smsOn) {
			emailOn = email != null;
			smsOn = !emailOn;
		}

		this.name = Objects.requireNonNull(name, "Name is required");
		this.email = email == null ? null : email.value();
		this.phone = phone == null ? null : phone.value();
		this.preferences = new ContactPreferences(emailOn, smsOn, preferences.language());

		registerEvent(new CustomerContactDetailsChanged(id, now));
	}

	public void changeContactPreferences(boolean emailEnabled, boolean smsEnabled, String language, Instant now) {

		assertActive("changed");

		if ((emailEnabled && email == null) || (smsEnabled && phone == null)) {
			throw new CustomerRuleViolation(RuleCode.CHANNEL_WITHOUT_CONTACT,
					"A channel can only be enabled when its contact detail is present");
		}
		if (!emailEnabled && !smsEnabled) {
			throw new CustomerRuleViolation(RuleCode.NO_CHANNEL_ENABLED,
					"At least one channel must stay enabled for messages about your appointments");
		}

		this.preferences = new ContactPreferences(emailEnabled, smsEnabled, language);
		registerEvent(new ContactPreferencesChanged(id, now));
	}

	/**
	 * Links a staff-created customer to the login account whose verified email matches.
	 */
	public void linkIdentity(IdentitySubject subject, Instant now) {

		assertNotErased();

		if (identitySubject != null) {
			throw CustomerRuleViolation.detailsUnavailable();
		}

		this.identitySubject = Objects.requireNonNull(subject).value();
		registerEvent(new CustomerIdentityLinked(id, now));
	}

	public void deactivate(Instant now) {

		assertStatus(CustomerStatus.ACTIVE, "deactivated");

		status = CustomerStatus.DEACTIVATED;
		registerEvent(new CustomerDeactivated(id, now));
	}

	public void reactivate(Instant now) {

		assertStatus(CustomerStatus.DEACTIVATED, "reactivated");

		status = CustomerStatus.ACTIVE;
		registerEvent(new CustomerReactivated(id, now));
	}

	/**
	 * Removes all personal data for good. The ID stays valid; the row is kept. A linked login account is reported for
	 * deletion first, while the subject is still known.
	 */
	public void erase(Instant now) {

		assertNotErased();

		if (identitySubject != null) {
			registerEvent(new LoginAccountErasureRequested(new IdentitySubject(identitySubject)));
		}

		name = null;
		email = null;
		phone = null;
		identitySubject = null;
		preferences = new ContactPreferences(false, false, preferences.language());
		status = CustomerStatus.ERASED;

		registerEvent(new CustomerErased(id, now));
	}

	public boolean isActive() {
		return status == CustomerStatus.ACTIVE;
	}

	public boolean isErased() {
		return status == CustomerStatus.ERASED;
	}

	private void assertContactPresent() {
		if (email == null && phone == null) {
			throw new CustomerRuleViolation(RuleCode.CONTACT_REQUIRED, "At least one of email or phone is required");
		}
	}

	private void assertActive(String action) {
		assertNotErased();
		if (status != CustomerStatus.ACTIVE) {
			throw new CustomerRuleViolation(RuleCode.INVALID_STATE,
					"A " + status + " customer cannot be " + action);
		}
	}

	private void assertNotErased() {
		if (status == CustomerStatus.ERASED) {
			throw new CustomerRuleViolation(RuleCode.INVALID_STATE, "An erased customer accepts no changes");
		}
	}

	private void assertStatus(CustomerStatus expected, String action) {
		assertNotErased();
		if (status != expected) {
			throw new CustomerRuleViolation(RuleCode.INVALID_STATE,
					"Only a customer who is " + expected + " can be " + action + ", but this one is " + status);
		}
	}

	@Override
	public CustomerId getId() {
		return id;
	}

	public @Nullable IdentitySubject getIdentitySubject() {
		return identitySubject == null ? null : new IdentitySubject(identitySubject);
	}

	public @Nullable PersonName getName() {
		return name;
	}

	public @Nullable EmailAddress getEmail() {
		return email == null ? null : new EmailAddress(email);
	}

	public @Nullable PhoneNumber getPhone() {
		return phone == null ? null : new PhoneNumber(phone);
	}

	public ContactPreferences getPreferences() {
		return preferences;
	}

	public CustomerStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Long getVersion() {
		return version;
	}
}
