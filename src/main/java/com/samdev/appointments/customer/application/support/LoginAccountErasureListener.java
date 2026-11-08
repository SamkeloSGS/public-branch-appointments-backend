package com.samdev.appointments.customer.application.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.samdev.appointments.customer.domain.event.LoginAccountErasureRequested;
import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.identity.LoginAccounts;
import com.samdev.appointments.identity.Subject;

/**
 * After an erasure commits, deletes the person's Keycloak login account through Identity, unless they are also staff
 * ({@code INVITED}, {@code ACTIVE} or {@code SUSPENDED}): staff keep their account. If Keycloak cannot be reached the
 * listener fails and the event publication registry delivers it again.
 */
@Component
class LoginAccountErasureListener {

	private static final Logger LOG = LoggerFactory.getLogger(LoginAccountErasureListener.class);

	private final AccessControl access;
	private final LoginAccounts accounts;

	LoginAccountErasureListener(AccessControl access, LoginAccounts accounts) {
		this.access = access;
		this.accounts = accounts;
	}

	@ApplicationModuleListener
	void on(LoginAccountErasureRequested event) {

		var subject = new Subject(event.subject().value());

		if (access.isStaff(subject)) {
			LOG.info("Kept the login account of an erased customer who is also staff");
			return;
		}

		accounts.delete(subject);
	}
}
