package com.samdev.appointments.identity.application.handler;

import java.time.Clock;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.application.command.ActivateStaffMember;
import com.samdev.appointments.identity.application.command.ChangeRole;
import com.samdev.appointments.identity.application.command.InviteStaffMember;
import com.samdev.appointments.identity.application.command.ReinstateStaffMember;
import com.samdev.appointments.identity.application.command.ReinviteStaffMember;
import com.samdev.appointments.identity.application.command.RemoveStaffMember;
import com.samdev.appointments.identity.application.command.SuspendStaffMember;
import com.samdev.appointments.identity.application.exception.StaffNotFoundException;
import com.samdev.appointments.identity.application.exception.StaleStaffVersionException;
import com.samdev.appointments.identity.application.port.StaffChangeLog.Action;
import com.samdev.appointments.identity.application.port.StaffChangeLog;
import com.samdev.appointments.identity.domain.entity.StaffMember;
import com.samdev.appointments.identity.domain.exception.RuleCode;
import com.samdev.appointments.identity.domain.exception.StaffRuleViolation;
import com.samdev.appointments.identity.domain.repository.StaffMembers;
import com.samdev.appointments.identity.domain.vo.EmailAddress;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

/**
 * Handles the staff commands. Each loads one staff member, applies the rules that need other staff members, calls
 * one behaviour method, saves it (publishing one event) and writes one change-log row, all in one transaction.
 */
@Component
public class StaffCommandHandler {

	private final StaffMembers staff;
	private final StaffChangeLog changeLog;
	private final Clock clock;

	StaffCommandHandler(StaffMembers staff, StaffChangeLog changeLog, Clock clock) {
		this.staff = staff;
		this.changeLog = changeLog;
		this.clock = clock;
	}

	@Transactional
	public StaffMemberId handle(InviteStaffMember command) {

		var email = new EmailAddress(command.email());

		if (staff.existsByEmailAndStatusNot(email.value(), StaffStatus.REMOVED)) {
			throw new StaffRuleViolation(RuleCode.EMAIL_IN_USE, "A staff member with this email already exists");
		}

		var member = StaffMember.invite(StaffMemberId.random(), email, command.displayName(), command.role(),
				clock.instant());

		staff.save(member);
		changeLog.record(member.getId(), Action.INVITED, command.actor(), describe(member));

		return member.getId();
	}

	@Transactional
	public void handle(ReinviteStaffMember command) {

		var member = load(command.staffMemberId(), command.expectedVersion(), command.actor());

		member.reinvite(clock.instant());
		save(member, Action.REINVITED, command.actor(), "invitation window restarted");
	}

	/**
	 * Links the subject to the unexpired invitation with this (verified) email. Called by the current-user
	 * resolution, never by a person.
	 */
	@Transactional
	public StaffMemberId handle(ActivateStaffMember command) {

		var email = new EmailAddress(command.email());
		var member = staff.findByEmailAndStatus(email.value(), StaffStatus.INVITED)
				.orElseThrow(() -> new StaffRuleViolation(RuleCode.INVALID_STATE, "No open invitation for this email"));

		member.activate(command.subject(), clock.instant());
		save(member, Action.ACTIVATED, null, "activated at first sign-in");

		return member.getId();
	}

	@Transactional
	public void handle(ChangeRole command) {

		var member = load(command.staffMemberId(), command.expectedVersion(), command.actor());
		var before = describe(member);

		if (!command.role().includes(StaffRole.BRANCH_MANAGER)) {
			assertNotLastBranchManager(member);
		}

		member.changeRole(command.role(), clock.instant());
		save(member, Action.ROLE_CHANGED, command.actor(), before + " -> " + describe(member));
	}

	@Transactional
	public void handle(SuspendStaffMember command) {

		var member = load(command.staffMemberId(), command.expectedVersion(), command.actor());
		assertNotLastBranchManager(member);

		member.suspend(clock.instant());
		save(member, Action.SUSPENDED, command.actor(), "ACTIVE -> SUSPENDED");
	}

	@Transactional
	public void handle(ReinstateStaffMember command) {

		var member = load(command.staffMemberId(), command.expectedVersion(), command.actor());

		member.reinstate(clock.instant());
		save(member, Action.REINSTATED, command.actor(), "SUSPENDED -> ACTIVE");
	}

	@Transactional
	public void handle(RemoveStaffMember command) {

		var member = load(command.staffMemberId(), command.expectedVersion(), command.actor());
		var before = member.getStatus();
		assertNotLastBranchManager(member);

		member.remove(clock.instant());
		save(member, Action.REMOVED, command.actor(), before + " -> REMOVED");
	}

	private StaffMember load(StaffMemberId id, @Nullable Long expectedVersion, @Nullable StaffMemberId actor) {

		if (id.equals(actor)) {
			throw new StaffRuleViolation(RuleCode.SELF_CHANGE_NOT_ALLOWED, "You cannot change your own access");
		}

		var member = staff.findById(id).orElseThrow(() -> new StaffNotFoundException(id));
		StaleStaffVersionException.check(id, expectedVersion, member.getVersion());

		return member;
	}

	private void save(StaffMember member, Action action, @Nullable StaffMemberId actor, String detail) {
		staff.save(member);
		changeLog.record(member.getId(), action, actor, detail);
	}

	private void assertNotLastBranchManager(StaffMember member) {
		if (member.isActiveBranchManager()
				&& staff.countByRoleAndStatus(StaffRole.BRANCH_MANAGER, StaffStatus.ACTIVE) <= 1) {
			throw new StaffRuleViolation(RuleCode.LAST_BRANCH_MANAGER,
					"The last active branch manager cannot be suspended, removed or given a lower role");
		}
	}

	/**
	 * The role, never the email address.
	 */
	private static String describe(StaffMember member) {
		return member.getRole().name();
	}
}
