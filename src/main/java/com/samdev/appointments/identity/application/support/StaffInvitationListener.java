package com.samdev.appointments.identity.application.support;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.samdev.appointments.identity.StaffMemberInvited;
import com.samdev.appointments.identity.StaffMemberRemoved;
import com.samdev.appointments.identity.application.port.StaffAccounts;
import com.samdev.appointments.identity.domain.entity.StaffMember;
import com.samdev.appointments.identity.domain.repository.StaffMembers;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

/**
 * After an invitation (or re-invitation) commits, emails the person their setup link through {@link StaffAccounts};
 * after an invitation is withdrawn, deletes the login created for it. Reads the staff member at delivery time, so a
 * retried event acts on the current state. If Keycloak cannot be reached the listener fails and the event
 * publication registry delivers it again.
 */
@Component
public class StaffInvitationListener {

	private static final Logger LOG = LoggerFactory.getLogger(StaffInvitationListener.class);

	private final StaffMembers staff;
	private final StaffAccounts accounts;
	private final Clock clock;

	StaffInvitationListener(StaffMembers staff, StaffAccounts accounts, Clock clock) {
		this.staff = staff;
		this.accounts = accounts;
		this.clock = clock;
	}

	@ApplicationModuleListener
	public void on(StaffMemberInvited event) {

		var member = staff.findById(event.staffMemberId()).orElse(null);

		if (member == null || member.getStatus() != StaffStatus.INVITED || member.getEmail() == null
				|| member.isInvitationExpired(clock.instant())) {
			LOG.info("Invitation no longer open; no setup email sent");
			return;
		}

		accounts.invite(member.getId(), member.getEmail(), member.getDisplayName(),
				member.getInvitedAt().plus(StaffMember.INVITATION_VALIDITY));
	}

	@ApplicationModuleListener
	public void on(StaffMemberRemoved event) {
		if (event.invitationWithdrawn()) {
			accounts.withdraw(event.staffMemberId());
		}
	}
}
