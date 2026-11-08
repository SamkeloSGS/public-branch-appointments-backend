package com.samdev.appointments.identity;

/**
 * Deletes Keycloak login accounts, for erasure. Other modules decide when; this module owns the Keycloak call.
 */
public interface LoginAccounts {

	/**
	 * Deletes the login account with this subject. An account that no longer exists counts as deleted.
	 *
	 * @throws IllegalStateException if Keycloak cannot be reached or refuses, so the caller's event is retried
	 */
	void delete(Subject subject);
}
