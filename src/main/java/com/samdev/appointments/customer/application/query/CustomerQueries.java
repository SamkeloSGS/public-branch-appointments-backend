package com.samdev.appointments.customer.application.query;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.IdentitySubject;
import com.samdev.appointments.customer.application.exception.CustomerNotFoundException;
import com.samdev.appointments.customer.application.port.AccessAudit;
import com.samdev.appointments.customer.application.port.AccessAudit.Action;
import com.samdev.appointments.customer.application.port.CustomerSettings;
import com.samdev.appointments.customer.domain.entity.Customer;
import com.samdev.appointments.customer.domain.repository.Customers;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;
import com.samdev.appointments.customer.domain.vo.EmailAddress;
import com.samdev.appointments.customer.domain.vo.PhoneNumber;
import com.samdev.appointments.identity.Subject;

/**
 * Reads for customers themselves and for staff. Staff reads write their audit row in the same transaction before any
 * data is returned, so a failed audit write returns nothing.
 */
@Component
public class CustomerQueries {

	public static final int MIN_QUERY_LENGTH = 3;
	public static final int MAX_QUERY_LENGTH = 200;

	private final Customers customers;
	private final AccessAudit audit;
	private final CustomerSettings settings;

	CustomerQueries(Customers customers, AccessAudit audit, CustomerSettings settings) {
		this.customers = customers;
		this.audit = audit;
		this.settings = settings;
	}

	/**
	 * The profile linked to the signed-in user. Not audited.
	 */
	@Transactional(readOnly = true)
	public Customer mine(IdentitySubject subject) {
		return customers.findByIdentitySubject(subject.value()).orElseThrow(CustomerNotFoundException::noProfile);
	}

	/**
	 * A customer by ID, without audit: for responses to commands, which are audited by the command itself.
	 */
	@Transactional(readOnly = true)
	public Customer get(CustomerId id) {
		return customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
	}

	/**
	 * Staff view of one customer, audited as {@code VIEW}.
	 */
	@Transactional
	public Customer view(Subject staff, CustomerId id) {

		var customer = get(id);
		audit.record(staff, Action.VIEW, id, null);

		return customer;
	}

	/**
	 * One page of the non-erased customers matching by name prefix, exact email or exact phone, by family and given
	 * name. Audited as {@code SEARCH} with the query text, once per page.
	 *
	 * @throws IllegalArgumentException for a query shorter than {@value #MIN_QUERY_LENGTH} or longer than
	 *     {@value #MAX_QUERY_LENGTH} characters
	 */
	@Transactional
	public Page<Customer> search(Subject staff, String query, Pageable page) {

		var text = query == null ? "" : query.strip();

		if (text.length() < MIN_QUERY_LENGTH || text.length() > MAX_QUERY_LENGTH) {
			throw new IllegalArgumentException("The search query must be " + MIN_QUERY_LENGTH + " to "
					+ MAX_QUERY_LENGTH + " characters");
		}

		audit.record(staff, Action.SEARCH, null, text);

		var lower = text.toLowerCase(Locale.ROOT);
		var namePrefix = lower.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";

		return customers.search(namePrefix, emailOrBlank(text), phoneOrBlank(text), CustomerStatus.ERASED, page);
	}

	private static String emailOrBlank(String text) {
		try {
			return new EmailAddress(text).value();
		} catch (IllegalArgumentException e) {
			return "";
		}
	}

	private String phoneOrBlank(String text) {
		try {
			return PhoneNumber.parse(text, settings.defaultCountryCode()).value();
		} catch (IllegalArgumentException e) {
			return "";
		}
	}
}
