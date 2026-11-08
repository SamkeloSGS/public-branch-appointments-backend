package com.samdev.appointments.customer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.samdev.appointments.SharedPersistenceConfiguration;
import com.samdev.appointments.TestcontainersConfiguration;
import com.samdev.appointments.customer.application.command.EraseCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomerByStaff;
import com.samdev.appointments.customer.application.handler.CustomerCommandHandler;
import com.samdev.appointments.identity.IdentityFixtures;
import com.samdev.appointments.identity.LoginAccounts;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.Subject;

/**
 * Erasing a customer deletes their Keycloak login after the commit, through Identity, unless the person is staff.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import({ TestcontainersConfiguration.class, SharedPersistenceConfiguration.class })
class LoginAccountErasureTests {

	static final Subject MANAGER = new Subject("manager-" + UUID.randomUUID());

	@MockitoBean LoginAccounts accounts;

	@Autowired CustomerCommandHandler commands;
	@Autowired IdentityFixtures identity;

	@Test
	@DisplayName("IA-15/CU-7 erasing a customer with a login deletes that Keycloak account")
	void deletesLogin() {

		var subject = UUID.randomUUID().toString();
		var id = commands.handle(new RegisterCustomer(new IdentitySubject(subject), null, "Thandi", "Nkosi",
				email(), null, null));

		commands.handle(new EraseCustomer(null, id, null));

		verify(accounts, timeout(10_000)).delete(new Subject(subject));
	}

	@Test
	@DisplayName("IA-15 a customer who is also staff keeps their login when the customer profile is erased")
	void keepsStaffLogin() {

		var staff = identity.activeStaff(StaffRole.BRANCH_STAFF);
		var id = commands.handle(new RegisterCustomer(new IdentitySubject(staff.subject().value()), null, "Lerato",
				"Mokoena", email(), null, null));

		commands.handle(new EraseCustomer(MANAGER, id, null));

		verify(accounts, after(2_000).never()).delete(staff.subject());
	}

	@Test
	@DisplayName("IA-15 a staff-created customer without a login deletes nothing")
	void noLoginNoDeletion() {

		var id = commands.handle(new RegisterCustomerByStaff(MANAGER, "Walk", "In", email(), null, null));

		commands.handle(new EraseCustomer(MANAGER, id, null));

		verify(accounts, after(2_000).never()).delete(any());
	}

	private static String email() {
		return "erase-" + UUID.randomUUID().toString().substring(0, 8) + "@customers.test";
	}
}
