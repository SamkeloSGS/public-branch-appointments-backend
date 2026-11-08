package com.samdev.appointments.customer.application.port;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.identity.Subject;

/**
 * Append-only record of staff searching, viewing or changing customer data. Written in the caller's transaction: if
 * the row cannot be written, the request fails and returns no data. Customer self-service and facade calls are not
 * audited.
 */
public interface AccessAudit {

	enum Action {
		SEARCH, VIEW, CREATE, UPDATE, DEACTIVATE, REACTIVATE, ERASE
	}

	/**
	 * @param customerId {@code null} for searches
	 * @param searchQuery the query text of a search, {@code null} otherwise
	 */
	void record(Subject staff, Action action, @Nullable CustomerId customerId, @Nullable String searchQuery);
}
