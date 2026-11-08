package com.samdev.appointments.identity.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.samdev.appointments.identity.StaffMemberActivated;
import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffMemberInvited;
import com.samdev.appointments.identity.StaffMemberRemoved;
import com.samdev.appointments.identity.StaffMemberSuspended;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.StaffRoleChanged;
import com.samdev.appointments.identity.Subject;
import com.samdev.appointments.identity.domain.exception.RuleCode;
import com.samdev.appointments.identity.domain.exception.StaffRuleViolation;
import com.samdev.appointments.identity.domain.vo.EmailAddress;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

class StaffMemberTests {

	static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");

	@Test
	@DisplayName("IA-5 an invitation is INVITED, stores the email lowercase and registers StaffMemberInvited")
	void invites() {

		var member = staff();

		assertThat(member.getStatus()).isEqualTo(StaffStatus.INVITED);
		assertThat(member.getEmail()).isEqualTo(new EmailAddress("thandi@capitec.test"));
		assertThat(member.getSubject()).isNull();

		var event = (StaffMemberInvited) single(member);
		assertThat(event.role()).isEqualTo(StaffRole.BRANCH_STAFF);
	}

	@Test
	@DisplayName("IA-5 an invitation needs a valid email and a display name")
	void invitationNeedsEmailAndName() {

		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("not-an-email"));
		assertThatIllegalArgumentException().isThrownBy(() -> StaffMember.invite(StaffMemberId.random(),
				new EmailAddress("a@b.test"), " ", StaffRole.BRANCH_MANAGER, NOW));
	}

	@Test
	@DisplayName("IA-6 activation links the subject within 14 days of the invitation")
	void activates() {

		var member = staff();
		clear(member);

		member.activate(new Subject("kc-1"), NOW.plus(Duration.ofDays(14)));

		assertThat(member.getStatus()).isEqualTo(StaffStatus.ACTIVE);
		assertThat(member.getSubject()).isEqualTo(new Subject("kc-1"));
		assertThat(single(member)).isInstanceOf(StaffMemberActivated.class);
	}

	@Test
	@DisplayName("IA-6 an invitation older than 14 days is not activated; re-inviting restarts the window")
	void expiredInvitation() {

		var member = staff();
		var later = NOW.plus(Duration.ofDays(15));

		assertThat(member.isInvitationExpired(later)).isTrue();
		assertThatExceptionOfType(StaffRuleViolation.class).isThrownBy(() -> member.activate(new Subject("x"), later));

		member.reinvite(later);
		member.activate(new Subject("x"), later.plusSeconds(60));
		assertThat(member.getStatus()).isEqualTo(StaffStatus.ACTIVE);
	}

	@Test
	@DisplayName("IA-7 changing the role registers StaffRoleChanged")
	void changesRole() {

		var member = activeStaff();

		member.changeRole(StaffRole.BRANCH_MANAGER, NOW);

		assertThat(member.getRole()).isEqualTo(StaffRole.BRANCH_MANAGER);
		assertThat(member.isActiveBranchManager()).isTrue();
		assertThat(((StaffRoleChanged) single(member)).role()).isEqualTo(StaffRole.BRANCH_MANAGER);
	}

	@Test
	@DisplayName("IA-9 ACTIVE and SUSPENDED transitions")
	void suspendsAndReinstates() {

		var member = activeStaff();

		member.suspend(NOW);
		assertThat(member.getStatus()).isEqualTo(StaffStatus.SUSPENDED);
		assertThat(single(member)).isInstanceOf(StaffMemberSuspended.class);

		assertThatExceptionOfType(StaffRuleViolation.class).isThrownBy(() -> member.suspend(NOW));

		member.reinstate(NOW);
		assertThat(member.getStatus()).isEqualTo(StaffStatus.ACTIVE);
		assertThat(member.getRole()).isEqualTo(StaffRole.BRANCH_STAFF);

		var invited = staff();
		assertThatExceptionOfType(StaffRuleViolation.class).isThrownBy(() -> invited.suspend(NOW))
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(RuleCode.INVALID_STATE));
	}

	@Test
	@DisplayName("IA-10 removal clears email and subject, keeps the name, and is terminal")
	void removes() {

		var member = activeStaff();
		member.remove(NOW);

		assertThat(member.getStatus()).isEqualTo(StaffStatus.REMOVED);
		assertThat(member.getEmail()).isNull();
		assertThat(member.getSubject()).isNull();
		assertThat(member.getDisplayName()).isEqualTo("Thandi");
		assertThat(single(member)).isInstanceOfSatisfying(StaffMemberRemoved.class,
				it -> assertThat(it.invitationWithdrawn()).as("was active, not an open invitation").isFalse());

		assertThatExceptionOfType(StaffRuleViolation.class).isThrownBy(() -> member.remove(NOW));
		assertThatExceptionOfType(StaffRuleViolation.class)
				.isThrownBy(() -> member.changeRole(StaffRole.BRANCH_MANAGER, NOW));
		assertThatExceptionOfType(StaffRuleViolation.class).isThrownBy(() -> member.reinstate(NOW));
	}

	@Test
	@DisplayName("IA-10 removing an open invitation says it was withdrawn, so its unused login can be deleted")
	void withdrawsInvitation() {

		var member = staff();
		clear(member);
		member.remove(NOW);

		assertThat(single(member)).isInstanceOfSatisfying(StaffMemberRemoved.class,
				it -> assertThat(it.invitationWithdrawn()).isTrue());
	}

	@Test
	@DisplayName("IA-2 role order: the branch manager includes branch staff")
	void roleOrder() {

		assertThat(StaffRole.BRANCH_MANAGER.includes(StaffRole.BRANCH_STAFF)).isTrue();
		assertThat(StaffRole.BRANCH_MANAGER.includes(StaffRole.BRANCH_MANAGER)).isTrue();
		assertThat(StaffRole.BRANCH_STAFF.includes(StaffRole.BRANCH_STAFF)).isTrue();
		assertThat(StaffRole.BRANCH_STAFF.includes(StaffRole.BRANCH_MANAGER)).isFalse();
	}

	private static StaffMember staff() {
		return StaffMember.invite(StaffMemberId.random(), new EmailAddress(" Thandi@Capitec.TEST "), "Thandi",
				StaffRole.BRANCH_STAFF, NOW);
	}

	private static StaffMember activeStaff() {
		var member = staff();
		member.activate(new Subject("kc-" + UUID.randomUUID()), NOW);
		clear(member);
		return member;
	}

	private static Object single(StaffMember member) {
		Collection<?> events = ReflectionTestUtils.invokeMethod(member, "domainEvents");
		assertThat(events).hasSize(1);
		return events.iterator().next();
	}

	private static void clear(StaffMember member) {
		ReflectionTestUtils.invokeMethod(member, "clearDomainEvents");
	}
}
