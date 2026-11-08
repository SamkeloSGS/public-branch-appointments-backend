package com.samdev.appointments.customer.application.handler;

import java.time.Clock;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.application.command.ChangeContactPreferences;
import com.samdev.appointments.customer.application.command.DeactivateCustomer;
import com.samdev.appointments.customer.application.command.EraseCustomer;
import com.samdev.appointments.customer.application.command.ReactivateCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomerByStaff;
import com.samdev.appointments.customer.application.command.UpdateContactDetails;
import com.samdev.appointments.customer.application.exception.CustomerNotFoundException;
import com.samdev.appointments.customer.application.exception.StaleCustomerVersionException;
import com.samdev.appointments.customer.application.port.AccessAudit;
import com.samdev.appointments.customer.application.port.AccessAudit.Action;
import com.samdev.appointments.customer.application.port.CustomerSettings;
import com.samdev.appointments.customer.domain.entity.Customer;
import com.samdev.appointments.customer.domain.exception.CustomerRuleViolation;
import com.samdev.appointments.customer.domain.exception.EmailInUseException;
import com.samdev.appointments.customer.domain.exception.RuleCode;
import com.samdev.appointments.customer.domain.repository.Customers;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;
import com.samdev.appointments.customer.domain.vo.EmailAddress;
import com.samdev.appointments.customer.domain.vo.PersonName;
import com.samdev.appointments.customer.domain.vo.PhoneNumber;
import com.samdev.appointments.identity.Subject;

/**
 * Handles the customer commands. Each loads or creates one customer, calls one behaviour method and saves it,
 * publishing exactly one event, in one transaction. Commands issued by staff also write one access-audit row in the
 * same transaction.
 * <p>
 * Email uniqueness among non-erased customers is checked here; the partial unique index is the backstop. Customers
 * are never told that an email belongs to someone else; staff get that customer's ID.
 */
@Component
public class CustomerCommandHandler {

	private final Customers customers;
	private final AccessAudit audit;
	private final CustomerSettings settings;
	private final Clock clock;

	CustomerCommandHandler(Customers customers, AccessAudit audit, CustomerSettings settings, Clock clock) {
		this.customers = customers;
		this.audit = audit;
		this.settings = settings;
		this.clock = clock;
	}

	/**
	 * Creates a profile linked to the subject. If an unlinked, non-erased customer has the user's verified email,
	 * links that customer instead and keeps its details (CU-2).
	 */
	@Transactional
	public CustomerId handle(RegisterCustomer command) {

		if (customers.findByIdentitySubject(command.subject().value()).isPresent()) {
			throw new CustomerRuleViolation(RuleCode.PROFILE_EXISTS, "You already have a customer profile");
		}

		var verifiedEmail = email(command.verifiedEmail());

		if (verifiedEmail != null) {

			var match = customers.findByEmailAndStatusNot(verifiedEmail.value(), CustomerStatus.ERASED);

			if (match.isPresent()) {
				var customer = match.get();
				customer.linkIdentity(command.subject(), clock.instant());
				customers.save(customer);
				return customer.getId();
			}
		}

		var name = new PersonName(command.givenName(), command.familyName());
		var email = email(command.email());
		var phone = phone(command.phone());

		if (email == null) {
			email = verifiedEmail;
		}

		assertContactPresent(email, phone);
		assertEmailFree(email, null, false);

		var customer = Customer.register(CustomerId.random(), command.subject(), name, email, phone,
				settings.supportedLanguage(command.language()), clock.instant());

		return customers.save(customer).getId();
	}

	@Transactional
	public CustomerId handle(RegisterCustomerByStaff command) {

		var name = new PersonName(command.givenName(), command.familyName());
		var email = email(command.email());
		var phone = phone(command.phone());

		assertContactPresent(email, phone);
		assertEmailFree(email, null, true);

		var customer = Customer.registerByStaff(CustomerId.random(), name, email, phone,
				settings.supportedLanguage(command.language()), clock.instant());

		customers.save(customer);
		audit.record(command.staff(), Action.CREATE, customer.getId(), null);

		return customer.getId();
	}

	@Transactional
	public void handle(UpdateContactDetails command) {

		var customer = load(command.customerId(), command.expectedVersion());
		var name = new PersonName(command.givenName(), command.familyName());
		var email = email(command.email());
		var phone = phone(command.phone());

		assertEmailFree(email, customer.getId(), command.staff() != null);

		customer.updateContactDetails(name, email, phone, clock.instant());
		save(customer, command.staff(), Action.UPDATE);
	}

	@Transactional
	public void handle(ChangeContactPreferences command) {

		var customer = load(command.customerId(), command.expectedVersion());
		var language = command.language() == null
				? customer.getPreferences().language()
				: settings.supportedLanguage(command.language());

		customer.changeContactPreferences(command.emailEnabled(), command.smsEnabled(), language, clock.instant());
		customers.save(customer);
	}

	@Transactional
	public void handle(DeactivateCustomer command) {

		var customer = load(command.customerId(), command.expectedVersion());

		customer.deactivate(clock.instant());
		save(customer, command.staff(), Action.DEACTIVATE);
	}

	@Transactional
	public void handle(ReactivateCustomer command) {

		var customer = load(command.customerId(), command.expectedVersion());

		customer.reactivate(clock.instant());
		save(customer, command.staff(), Action.REACTIVATE);
	}

	@Transactional
	public void handle(EraseCustomer command) {

		var customer = load(command.customerId(), command.expectedVersion());

		customer.erase(clock.instant());
		save(customer, command.staff(), Action.ERASE);
	}

	private Customer load(CustomerId id, @Nullable Long expectedVersion) {

		var customer = customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
		StaleCustomerVersionException.check(id, expectedVersion, customer.getVersion());

		return customer;
	}

	/**
	 * Saves, and audits when staff made the change.
	 */
	private void save(Customer customer, @Nullable Subject staff, Action action) {

		customers.save(customer);

		if (staff != null) {
			audit.record(staff, action, customer.getId(), null);
		}
	}

	private void assertEmailFree(@Nullable EmailAddress email, @Nullable CustomerId self, boolean staff) {

		if (email == null) {
			return;
		}

		customers.findByEmailAndStatusNot(email.value(), CustomerStatus.ERASED)
				.filter(it -> !it.getId().equals(self))
				.ifPresent(it -> {
					throw staff ? new EmailInUseException(it.getId()) : CustomerRuleViolation.detailsUnavailable();
				});
	}

	private static void assertContactPresent(@Nullable EmailAddress email, @Nullable PhoneNumber phone) {
		if (email == null && phone == null) {
			throw new IllegalArgumentException("At least one of email or phone is required");
		}
	}

	private static @Nullable EmailAddress email(@Nullable String value) {
		return value == null || value.isBlank() ? null : new EmailAddress(value);
	}

	private @Nullable PhoneNumber phone(@Nullable String value) {
		return value == null || value.isBlank() ? null : PhoneNumber.parse(value, settings.defaultCountryCode());
	}
}
