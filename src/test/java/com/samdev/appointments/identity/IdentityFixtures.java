package com.samdev.appointments.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.samdev.appointments.identity.application.command.ActivateStaffMember;
import com.samdev.appointments.identity.application.command.InviteStaffMember;
import com.samdev.appointments.identity.application.handler.StaffCommandHandler;

/**
 * Creates staff members through the module's own commands and issues tokens shaped like Keycloak's: subject,
 * verified email, sign-in level ({@code acr}) and the {@code appointments-api} audience. Roles are never in the
 * token.
 */
@Component
public class IdentityFixtures {

	private final StaffCommandHandler commands;

	IdentityFixtures(StaffCommandHandler commands) {
		this.commands = commands;
	}

	/**
	 * An invited and activated staff member, as after their first sign-in.
	 */
	public StaffAccount activeStaff(StaffRole role) {

		var account = newAccount();
		var id = commands.handle(new InviteStaffMember(null, account.email(), "Staff " + account.subject(), role));
		commands.handle(new ActivateStaffMember(account.subject(), account.email()));

		return new StaffAccount(id, account.subject(), account.email());
	}

	/**
	 * An invited staff member who has not signed in yet.
	 */
	public StaffAccount invitedStaff(StaffRole role) {

		var account = newAccount();
		var id = commands.handle(new InviteStaffMember(null, account.email(), "Staff " + account.subject(), role));

		return new StaffAccount(id, account.subject(), account.email());
	}

	/**
	 * Someone signed in with no staff record.
	 */
	public static Account newAccount() {
		var subject = UUID.randomUUID().toString();
		return new Account(new Subject(subject), "person-" + subject.substring(0, 8) + "@appointments.test");
	}

	public static RequestPostProcessor token(Account account, int level) {
		return token(account.subject(), account.email(), true, level);
	}

	public static RequestPostProcessor token(StaffAccount account, int level) {
		return token(account.subject(), account.email(), true, level);
	}

	public static RequestPostProcessor token(Subject subject, String email, boolean emailVerified, int level) {
		return jwt().jwt(it -> it.subject(subject.value())
				.claim("email", email)
				.claim("email_verified", emailVerified)
				.claim("acr", String.valueOf(level))
				.audience(List.of("appointments-api")));
	}

	public record Account(Subject subject, String email) {}

	public record StaffAccount(StaffMemberId id, Subject subject, String email) {}
}
