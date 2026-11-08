package com.samdev.appointments.branchmanagement.web.controller;

import static com.samdev.appointments.identity.IdentityFixtures.token;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.samdev.appointments.identity.IdentityFixtures;
import com.samdev.appointments.identity.IdentityFixtures.StaffAccount;
import com.samdev.appointments.identity.StaffRole;

/**
 * Authenticated callers backed by real staff records in the identity module, signed in at level 2 unless stated.
 * Create one per test with the context's {@link IdentityFixtures}.
 */
class Callers {

	private final IdentityFixtures identity;
	private StaffAccount manager;

	Callers(IdentityFixtures identity) {
		this.identity = identity;
	}

	RequestPostProcessor manager() {
		return token(managerAccount(), 2);
	}

	RequestPostProcessor managerAtLevelOne() {
		return token(managerAccount(), 1);
	}

	RequestPostProcessor staff() {
		return token(identity.activeStaff(StaffRole.BRANCH_STAFF), 2);
	}

	/**
	 * Signed in, but with no staff record: a customer.
	 */
	RequestPostProcessor customer() {
		return token(IdentityFixtures.newAccount(), 2);
	}

	private synchronized StaffAccount managerAccount() {
		if (manager == null) {
			manager = identity.activeStaff(StaffRole.BRANCH_MANAGER);
		}
		return manager;
	}
}
