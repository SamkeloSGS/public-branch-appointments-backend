package com.samdev.appointments.identity.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.identity.StaffMemberActivated;
import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffMemberInvited;
import com.samdev.appointments.identity.StaffMemberReinstated;
import com.samdev.appointments.identity.StaffMemberRemoved;
import com.samdev.appointments.identity.StaffMemberSuspended;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.StaffRoleChanged;
import com.samdev.appointments.identity.Subject;
import com.samdev.appointments.identity.domain.exception.RuleCode;
import com.samdev.appointments.identity.domain.exception.StaffRuleViolation;
import com.samdev.appointments.identity.domain.vo.EmailAddress;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

/**
 * A person with a staff role at the branch. Created as an invitation; linked to a Keycloak subject at first
 * sign-in.
 */
@Table(schema = "identity", name = "staff_member")
public class StaffMember extends AbstractAggregateRoot<StaffMember> implements AggregateRoot<StaffMember, StaffMemberId> {

	public static final Duration INVITATION_VALIDITY = Duration.ofDays(14);

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final StaffMemberId id;

	/**
	 * Empty until first sign-in, and again after removal.
	 */
	private @Nullable String subject;

	/**
	 * Lowercase; cleared on removal.
	 */
	private @Nullable String email;

	private String displayName;

	@Enumerated(EnumType.STRING)
	private StaffRole role;

	@Enumerated(EnumType.STRING)
	private StaffStatus status;

	private Instant invitedAt;

	private final Instant createdAt;

	@Version
	private Long version;

	private StaffMember(StaffMemberId id, EmailAddress email, String displayName, StaffRole role, Instant now) {

		this.id = Objects.requireNonNull(id);
		this.email = Objects.requireNonNull(email, "Email is required").value();
		this.displayName = validDisplayName(displayName);
		this.role = Objects.requireNonNull(role, "Role is required");
		this.status = StaffStatus.INVITED;
		this.invitedAt = now;
		this.createdAt = now;
	}

	public static StaffMember invite(StaffMemberId id, EmailAddress email, String displayName, StaffRole role,
			Instant now) {

		var member = new StaffMember(id, email, displayName, role, now);
		member.registerEvent(new StaffMemberInvited(id, role, now));
		return member;
	}

	/**
	 * Restarts the invitation window.
	 */
	public void reinvite(Instant now) {

		assertStatus(StaffStatus.INVITED, "re-invited");

		invitedAt = now;
		registerEvent(new StaffMemberInvited(id, role, now));
	}

	/**
	 * Links the Keycloak subject at first sign-in, within {@link #INVITATION_VALIDITY} of the invitation.
	 */
	public void activate(Subject subject, Instant now) {

		assertStatus(StaffStatus.INVITED, "activated");

		if (isInvitationExpired(now)) {
			throw new StaffRuleViolation(RuleCode.INVALID_STATE, "The invitation has expired");
		}

		this.subject = Objects.requireNonNull(subject).value();
		this.status = StaffStatus.ACTIVE;
		registerEvent(new StaffMemberActivated(id, now));
	}

	public void changeRole(StaffRole role, Instant now) {

		assertNotRemoved();

		this.role = Objects.requireNonNull(role, "Role is required");
		registerEvent(new StaffRoleChanged(id, role, now));
	}

	public void suspend(Instant now) {

		assertStatus(StaffStatus.ACTIVE, "suspended");

		status = StaffStatus.SUSPENDED;
		registerEvent(new StaffMemberSuspended(id, now));
	}

	public void reinstate(Instant now) {

		assertStatus(StaffStatus.SUSPENDED, "reinstated");

		status = StaffStatus.ACTIVE;
		registerEvent(new StaffMemberReinstated(id, now));
	}

	/**
	 * Ends staff access for good. Email and subject are cleared; the display name is kept for the change log.
	 */
	public void remove(Instant now) {

		assertNotRemoved();

		var invitationWithdrawn = status == StaffStatus.INVITED;

		status = StaffStatus.REMOVED;
		email = null;
		subject = null;
		registerEvent(new StaffMemberRemoved(id, invitationWithdrawn, now));
	}

	public boolean isInvitationExpired(Instant now) {
		return invitedAt.plus(INVITATION_VALIDITY).isBefore(now);
	}

	/**
	 * Active staff with the branch manager role.
	 */
	public boolean isActiveBranchManager() {
		return status == StaffStatus.ACTIVE && role == StaffRole.BRANCH_MANAGER;
	}

	private void assertNotRemoved() {
		if (status == StaffStatus.REMOVED) {
			throw new StaffRuleViolation(RuleCode.INVALID_STATE, "A removed staff member accepts no changes");
		}
	}

	private void assertStatus(StaffStatus expected, String action) {
		if (status != expected) {
			throw new StaffRuleViolation(RuleCode.INVALID_STATE,
					"Only a staff member who is " + expected + " can be " + action + ", but this one is " + status);
		}
	}

	private static String validDisplayName(String displayName) {

		if (displayName == null || displayName.isBlank() || displayName.strip().length() > 120) {
			throw new IllegalArgumentException("Display name must be 1 to 120 characters");
		}

		return displayName.strip();
	}

	@Override
	public StaffMemberId getId() {
		return id;
	}

	public @Nullable Subject getSubject() {
		return subject == null ? null : new Subject(subject);
	}

	public @Nullable EmailAddress getEmail() {
		return email == null ? null : new EmailAddress(email);
	}

	public String getDisplayName() {
		return displayName;
	}

	public StaffRole getRole() {
		return role;
	}

	public StaffStatus getStatus() {
		return status;
	}

	public Instant getInvitedAt() {
		return invitedAt;
	}

	public Long getVersion() {
		return version;
	}
}
