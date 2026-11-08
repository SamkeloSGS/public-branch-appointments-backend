package com.samdev.appointments.customer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The read-only interface other modules use. Calls are system access and are not written to the access audit.
 */
public interface CustomerDirectory {

	/**
	 * Shown instead of the name of an erased or unknown customer.
	 */
	String FORMER_CUSTOMER = "Former customer";

	/**
	 * The customer linked to a signed-in user.
	 */
	Optional<CustomerId> findByIdentity(IdentitySubject subject);

	/**
	 * True only for an {@code ACTIVE} customer; false for deactivated, erased and unknown IDs.
	 */
	boolean isActive(CustomerId id);

	/**
	 * Display name, language, and the contact details of enabled channels only. Empty for erased or unknown
	 * customers.
	 */
	Optional<CustomerContact> contactFor(CustomerId id);

	/**
	 * An entry for every requested ID; erased and unknown customers map to {@link #FORMER_CUSTOMER}.
	 */
	Map<CustomerId, String> displayNames(Set<CustomerId> ids);
}
