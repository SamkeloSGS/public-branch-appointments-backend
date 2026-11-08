package com.samdev.appointments.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.test.context.TestPropertySource;

import com.samdev.appointments.SharedPersistenceConfiguration;
import com.samdev.appointments.TestcontainersConfiguration;
import com.samdev.appointments.identity.IdentityFixtures.StaffAccount;
import com.samdev.appointments.identity.application.command.ChangeRole;
import com.samdev.appointments.identity.application.command.InviteStaffMember;
import com.samdev.appointments.identity.application.command.ReinstateStaffMember;
import com.samdev.appointments.identity.application.command.ReinviteStaffMember;
import com.samdev.appointments.identity.application.command.RemoveStaffMember;
import com.samdev.appointments.identity.application.command.SuspendStaffMember;
import com.samdev.appointments.identity.application.handler.StaffCommandHandler;
import com.samdev.appointments.identity.application.port.StaffChangeLog.Action;
import com.samdev.appointments.identity.application.query.StaffQueries;
import com.samdev.appointments.identity.application.support.CurrentUserResolver;
import com.samdev.appointments.identity.application.support.TokenClaims;
import com.samdev.appointments.identity.domain.exception.RuleCode;
import com.samdev.appointments.identity.domain.exception.StaffRuleViolation;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

/**
 * Staff commands, current-user resolution and the change log against PostgreSQL.
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, SharedPersistenceConfiguration.class })
@TestPropertySource(properties = "appointments.identity.first-admin-email=first.admin@appointments.test")
class IdentityModuleTests {

	@Autowired StaffCommandHandler commands;
	@Autowired StaffQueries queries;
	@Autowired CurrentUserResolver resolver;
	@Autowired IdentityFixtures fixtures;
	@Autowired JdbcTemplate jdbc;

	@AfterEach
	void dropFailingTrigger() {
		jdbc.execute("DROP TRIGGER IF EXISTS fail_change_log ON identity.staff_change_log");
	}

	// IA-5, IA-6

	@Test
	@DisplayName("IA-5/IA-13 inviting publishes StaffMemberInvited and logs who invited, without the email")
	void invites(PublishedEvents events) {

		var actor = fixtures.activeStaff(StaffRole.BRANCH_MANAGER);

		var event = exactlyOne(events, StaffMemberInvited.class, () -> commands.handle(new InviteStaffMember(actor.id(),
				"Lerato.M@Capitec.test", "Lerato", StaffRole.BRANCH_STAFF)));

		var member = queries.get(event.staffMemberId());
		assertThat(member.getStatus()).isEqualTo(StaffStatus.INVITED);
		assertThat(member.getEmail().value()).isEqualTo("lerato.m@capitec.test");

		var log = queries.changes(member.getId());
		assertThat(log).singleElement().satisfies(it -> {
			assertThat(it.action()).isEqualTo(Action.INVITED);
			assertThat(it.changedBy()).isEqualTo(actor.id());
			assertThat(it.detail()).contains("BRANCH_STAFF").doesNotContain("@");
		});
	}

	@Test
	@DisplayName("IA-5 an email in use by a staff member who is not removed is rejected")
	void rejectsEmailInUse() {

		var existing = fixtures.invitedStaff(StaffRole.BRANCH_STAFF);

		assertThatExceptionOfType(StaffRuleViolation.class)
				.isThrownBy(() -> commands.handle(new InviteStaffMember(null, existing.email().toUpperCase(), "Again",
						StaffRole.BRANCH_MANAGER)))
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(RuleCode.EMAIL_IN_USE));
	}

	@Test
	@DisplayName("IA-6 a verified email matching an invitation activates it at first sign-in")
	void activatesAtFirstSignIn(PublishedEvents events) {

		var invited = fixtures.invitedStaff(StaffRole.BRANCH_MANAGER);

		var user = resolver.resolve(new TokenClaims(invited.subject(), invited.email(), true, 2));

		assertThat(user.staffRole()).isEqualTo(StaffRole.BRANCH_MANAGER);
		assertThat(user.staffMemberId()).isEqualTo(invited.id());
		assertThat(queries.get(invited.id()).getSubject()).isEqualTo(invited.subject());
		assertThat(events.ofType(StaffMemberActivated.class)).hasSize(1);
		assertThat(queries.changes(invited.id())).extracting(it -> it.action())
				.containsExactly(Action.INVITED, Action.ACTIVATED);
	}

	@Test
	@DisplayName("IA-6 an unverified email never activates an invitation")
	void unverifiedEmailDoesNotActivate() {

		var invited = fixtures.invitedStaff(StaffRole.BRANCH_STAFF);

		var user = resolver.resolve(new TokenClaims(invited.subject(), invited.email(), false, 2));

		assertThat(user.role()).isEqualTo(CurrentUser.CUSTOMER);
		assertThat(queries.get(invited.id()).getStatus()).isEqualTo(StaffStatus.INVITED);
	}

	@Test
	@DisplayName("IA-6 an invitation older than 14 days is not activated; the person is a customer")
	void expiredInvitationIsNotActivated() {

		var invited = fixtures.invitedStaff(StaffRole.BRANCH_STAFF);
		jdbc.update("UPDATE identity.staff_member SET invited_at = now() - interval '15 days' WHERE id = ?",
				invited.id().value());

		var user = resolver.resolve(new TokenClaims(invited.subject(), invited.email(), true, 2));

		assertThat(user.isStaff()).isFalse();
		assertThat(queries.get(invited.id()).getStatus()).isEqualTo(StaffStatus.INVITED);
	}

	@Test
	@DisplayName("IA-6 two simultaneous first requests activate the invitation once")
	void concurrentFirstRequestsActivateOnce() throws Exception {

		var invited = fixtures.invitedStaff(StaffRole.BRANCH_MANAGER);
		var claims = new TokenClaims(invited.subject(), invited.email(), true, 2);

		try (var executor = Executors.newFixedThreadPool(4)) {
			var results = executor.invokeAll(List.<Callable<CurrentUser>> of(() -> resolver.resolve(claims),
					() -> resolver.resolve(claims), () -> resolver.resolve(claims), () -> resolver.resolve(claims)));
			for (var result : results) {
				assertThat(result.get().staffMemberId()).isEqualTo(invited.id());
			}
		}

		assertThat(queries.changes(invited.id())).filteredOn(it -> it.action() == Action.ACTIVATED).hasSize(1);
	}

	// IA-7 to IA-11

	@Test
	@DisplayName("IA-7/IA-9 role, suspend and reinstate commands publish one event and one log row each")
	void lifecycle(PublishedEvents events) {

		var actor = fixtures.activeStaff(StaffRole.BRANCH_MANAGER);
		var member = fixtures.activeStaff(StaffRole.BRANCH_STAFF);

		exactlyOne(events, StaffRoleChanged.class, () -> {
			commands.handle(new ChangeRole(actor.id(), member.id(), null, StaffRole.BRANCH_MANAGER));
			return null;
		});
		exactlyOne(events, StaffMemberSuspended.class, () -> {
			commands.handle(new SuspendStaffMember(actor.id(), member.id(), null));
			return null;
		});

		assertThat(resolver.resolve(new TokenClaims(member.subject(), member.email(), true, 2)).isStaff())
				.as("a suspended staff member is treated as a customer").isFalse();

		exactlyOne(events, StaffMemberReinstated.class, () -> {
			commands.handle(new ReinstateStaffMember(actor.id(), member.id(), null));
			return null;
		});

		var user = resolver.resolve(new TokenClaims(member.subject(), member.email(), true, 2));
		assertThat(user.staffRole()).isEqualTo(StaffRole.BRANCH_MANAGER);

		assertThat(queries.changes(member.id())).extracting(it -> it.action()).containsExactly(Action.INVITED,
				Action.ACTIVATED, Action.ROLE_CHANGED, Action.SUSPENDED, Action.REINSTATED);
	}

	@Test
	@DisplayName("IA-10 removal clears email and subject; the same email can be invited again")
	void removeAndInviteAgain(PublishedEvents events) {

		var member = fixtures.activeStaff(StaffRole.BRANCH_STAFF);

		exactlyOne(events, StaffMemberRemoved.class, () -> {
			commands.handle(new RemoveStaffMember(null, member.id(), null));
			return null;
		});

		var removed = queries.get(member.id());
		assertThat(removed.getEmail()).isNull();
		assertThat(removed.getSubject()).isNull();
		assertThat(resolver.resolve(new TokenClaims(member.subject(), member.email(), true, 2)).isStaff()).isFalse();

		var again = commands.handle(new InviteStaffMember(null, member.email(), "Back again", StaffRole.BRANCH_STAFF));
		assertThat(again).isNotEqualTo(member.id());
	}

	@Test
	@DisplayName("IA-11 the last active branch manager cannot be suspended, removed or demoted; nobody changes themselves")
	void protectsAdministrativeAccess() {

		jdbc.update("UPDATE identity.staff_member SET status = 'SUSPENDED' WHERE role = 'BRANCH_MANAGER' AND status = 'ACTIVE'");
		var last = fixtures.activeStaff(StaffRole.BRANCH_MANAGER);

		for (Runnable command : List.<Runnable> of(
				() -> commands.handle(new SuspendStaffMember(null, last.id(), null)),
				() -> commands.handle(new RemoveStaffMember(null, last.id(), null)),
				() -> commands.handle(new ChangeRole(null, last.id(), null, StaffRole.BRANCH_STAFF)))) {
			assertThatExceptionOfType(StaffRuleViolation.class).isThrownBy(command::run)
					.satisfies(it -> assertThat(it.getCode()).isEqualTo(RuleCode.LAST_BRANCH_MANAGER));
		}

		var second = fixtures.activeStaff(StaffRole.BRANCH_MANAGER);

		assertThatExceptionOfType(StaffRuleViolation.class)
				.isThrownBy(() -> commands.handle(new SuspendStaffMember(second.id(), second.id(), null)))
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(RuleCode.SELF_CHANGE_NOT_ALLOWED));

		commands.handle(new SuspendStaffMember(second.id(), last.id(), null));
		assertThat(queries.get(last.id()).getStatus()).isEqualTo(StaffStatus.SUSPENDED);
	}

	// IA-12, IA-13, IA-15, IA-18

	@Test
	@DisplayName("IA-12 the configured first branch manager is invited once at start-up")
	void invitesFirstAdmin() {

		var firstAdmins = queries.list(StaffRole.BRANCH_MANAGER, null).stream()
				.filter(it -> it.getEmail() != null && it.getEmail().value().equals("first.admin@appointments.test"))
				.toList();

		assertThat(firstAdmins).hasSize(1);
	}

	@Test
	@DisplayName("IA-13 if the change-log row cannot be written, the command fails and nothing changes")
	void changeLogFailsClosed() {

		var member = fixtures.activeStaff(StaffRole.BRANCH_STAFF);
		jdbc.execute("""
				CREATE OR REPLACE FUNCTION identity.fail_change_log() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'change log unavailable'; END; $$;
				CREATE TRIGGER fail_change_log BEFORE INSERT ON identity.staff_change_log
				FOR EACH ROW WHEN (NEW.action = 'SUSPENDED') EXECUTE FUNCTION identity.fail_change_log();
				""");

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> commands.handle(new SuspendStaffMember(null, member.id(), null)));

		assertThat(queries.get(member.id()).getStatus()).isEqualTo(StaffStatus.ACTIVE);
	}

	@Test
	@DisplayName("IA-13 change-log rows are append-only")
	void changeLogIsAppendOnly() {

		var member = fixtures.invitedStaff(StaffRole.BRANCH_STAFF);

		assertThatExceptionOfType(Exception.class).isThrownBy(() -> jdbc.update(
				"UPDATE identity.staff_change_log SET detail = 'x' WHERE staff_member_id = ?", member.id().value()));
	}

	@Test
	@DisplayName("IA-15 isStaff is true for INVITED, ACTIVE and SUSPENDED staff only")
	void isStaff(@Autowired AccessControl access) {

		var active = fixtures.activeStaff(StaffRole.BRANCH_STAFF);
		assertThat(access.isStaff(active.subject())).isTrue();

		commands.handle(new SuspendStaffMember(null, active.id(), null));
		assertThat(access.isStaff(active.subject())).isTrue();

		commands.handle(new RemoveStaffMember(null, active.id(), null));
		assertThat(access.isStaff(active.subject())).isFalse();
		assertThat(access.isStaff(IdentityFixtures.newAccount().subject())).isFalse();
	}

	@Test
	@DisplayName("IA-18 an update based on an outdated version is rejected")
	void rejectsStaleVersion() {

		StaffAccount member = fixtures.invitedStaff(StaffRole.BRANCH_STAFF);
		var version = queries.get(member.id()).getVersion();

		commands.handle(new ReinviteStaffMember(null, member.id(), version));

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> commands.handle(new ReinviteStaffMember(null, member.id(), version)))
				.withMessageContaining("version");
	}

	private static <T> T exactlyOne(PublishedEvents events, Class<T> type, Supplier<?> command) {

		var before = domainEvents(events).size();
		command.get();
		var after = domainEvents(events);

		assertThat(after).hasSize(before + 1);
		assertThat(after.get(after.size() - 1)).isInstanceOf(type);

		return type.cast(after.get(after.size() - 1));
	}

	private static List<Object> domainEvents(PublishedEvents events) {
		var result = new ArrayList<Object>();
		events.ofType(Object.class).forEach(it -> {
			if (it.getClass().isAnnotationPresent(DomainEvent.class)) {
				result.add(it);
			}
		});
		return result;
	}
}
