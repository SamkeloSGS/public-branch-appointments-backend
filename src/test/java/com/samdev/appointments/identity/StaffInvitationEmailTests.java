package com.samdev.appointments.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.samdev.appointments.SharedPersistenceConfiguration;
import com.samdev.appointments.TestcontainersConfiguration;
import com.samdev.appointments.identity.application.command.InviteStaffMember;
import com.samdev.appointments.identity.application.command.ReinviteStaffMember;
import com.samdev.appointments.identity.application.command.RemoveStaffMember;
import com.samdev.appointments.identity.application.handler.StaffCommandHandler;
import com.samdev.appointments.identity.application.port.StaffAccounts;
import com.samdev.appointments.identity.application.support.StaffInvitationListener;
import com.samdev.appointments.identity.domain.entity.StaffMember;
import com.samdev.appointments.identity.domain.vo.EmailAddress;

/**
 * The setup email and the withdrawal of an unused login, each sent once the staff change has committed (the event
 * publication registry delivers the events to {@link StaffInvitationListener}).
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, SharedPersistenceConfiguration.class })
class StaffInvitationEmailTests {

	static final long WAIT = 10_000;

	@Autowired StaffCommandHandler commands;
	@Autowired StaffInvitationListener listener;
	@Autowired IdentityFixtures fixtures;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean StaffAccounts accounts;

	@Test
	@DisplayName("IA-5 an invitation emails the setup link after it commits, valid until the invitation expires")
	void emailsSetupLink() {

		var id = commands.handle(new InviteStaffMember(null, "Naledi.K@Capitec.test", "Naledi Khumalo",
				StaffRole.BRANCH_STAFF));
		var invitedAt = jdbc.queryForObject("SELECT invited_at FROM identity.staff_member WHERE id = ?",
				Instant.class, id.value());

		verify(accounts, timeout(WAIT)).invite(id, new EmailAddress("naledi.k@capitec.test"), "Naledi Khumalo",
				invitedAt.plus(StaffMember.INVITATION_VALIDITY));
	}

	@Test
	@DisplayName("IA-5 re-inviting sends the setup link again")
	void reinviteEmailsAgain() {

		var id = commands.handle(new InviteStaffMember(null, "Sipho.N@Capitec.test", "Sipho", StaffRole.BRANCH_STAFF));
		verify(accounts, timeout(WAIT)).invite(eq(id), any(), any(), any());

		commands.handle(new ReinviteStaffMember(null, id, null));

		verify(accounts, timeout(WAIT).times(2)).invite(eq(id), any(), any(), any());
	}

	@Test
	@DisplayName("IA-10 withdrawing an invitation deletes its unused login; removing active staff keeps theirs")
	void withdrawal() {

		var invited = commands.handle(new InviteStaffMember(null, "Ayanda.D@Capitec.test", "Ayanda",
				StaffRole.BRANCH_STAFF));
		commands.handle(new RemoveStaffMember(null, invited, null));
		verify(accounts, timeout(WAIT)).withdraw(invited);

		var active = fixtures.activeStaff(StaffRole.BRANCH_STAFF);
		commands.handle(new RemoveStaffMember(null, active.id(), null));
		verify(accounts, after(2_000).never()).withdraw(active.id());
	}

	@Test
	@DisplayName("IA-5 a retried event for an invitation that is no longer open sends nothing")
	void closedInvitation() {

		var id = commands.handle(new InviteStaffMember(null, "Zola.B@Capitec.test", "Zola", StaffRole.BRANCH_STAFF));
		verify(accounts, timeout(WAIT)).invite(eq(id), any(), any(), any());
		commands.handle(new RemoveStaffMember(null, id, null));

		listener.on(new StaffMemberInvited(id, StaffRole.BRANCH_STAFF, Instant.now()));

		verify(accounts, times(1)).invite(eq(id), any(), any(), any());
	}
}
