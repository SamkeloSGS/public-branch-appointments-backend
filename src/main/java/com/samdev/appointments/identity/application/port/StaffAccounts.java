package com.samdev.appointments.identity.application.port;

import java.time.Instant;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.domain.vo.EmailAddress;

/**
 * The Keycloak login behind a staff invitation. Called after the invitation commits; both calls are idempotent, so a
 * failed one can be retried.
 */
public interface StaffAccounts {

	/**
	 * Emails the invited person one link that gets them ready to sign in. Without a login, one is created for the
	 * invitation and the link verifies the email, sets a password and sets up an authenticator app. With an existing
	 * login, the link only verifies the email if that is still needed; otherwise nothing is sent and they sign in as
	 * usual. The link expires with the invitation.
	 *
	 * @throws IllegalStateException if Keycloak cannot be reached or refuses, so the event is delivered again
	 */
	void invite(StaffMemberId invitation, EmailAddress email, String displayName, Instant expiresAt);

	/**
	 * Deletes the login created for this invitation, if there is one. A login the person had before is never
	 * touched.
	 *
	 * @throws IllegalStateException if Keycloak cannot be reached or refuses, so the event is delivered again
	 */
	void withdraw(StaffMemberId invitation);
}
