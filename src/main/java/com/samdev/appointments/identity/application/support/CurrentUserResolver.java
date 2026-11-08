package com.samdev.appointments.identity.application.support;

import java.time.Clock;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.samdev.appointments.identity.CurrentUser;
import com.samdev.appointments.identity.application.command.ActivateStaffMember;
import com.samdev.appointments.identity.application.handler.StaffCommandHandler;
import com.samdev.appointments.identity.domain.entity.StaffMember;
import com.samdev.appointments.identity.domain.exception.StaffRuleViolation;
import com.samdev.appointments.identity.domain.repository.StaffMembers;
import com.samdev.appointments.identity.domain.vo.EmailAddress;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

/**
 * Turns a validated token into the {@link CurrentUser}. Looks the staff member up by subject on every call (nothing
 * is cached across requests, so a change made on one instance applies on all of them immediately), and activates a
 * matching invitation at first sign-in.
 */
@Component
public class CurrentUserResolver {

	private final StaffMembers staff;
	private final StaffCommandHandler commands;
	private final Clock clock;

	CurrentUserResolver(StaffMembers staff, StaffCommandHandler commands, Clock clock) {
		this.staff = staff;
		this.commands = commands;
		this.clock = clock;
	}

	public CurrentUser resolve(TokenClaims claims) {

		var email = claims.emailVerified() ? claims.email() : null;
		var member = staff.findBySubject(claims.subject().value()).or(() -> activateInvitation(claims));

		return member
				.filter(it -> it.getStatus() == StaffStatus.ACTIVE)
				.map(it -> new CurrentUser(claims.subject(), email, it.getRole(), it.getId(), claims.secondFactor()))
				.orElseGet(() -> new CurrentUser(claims.subject(), email, null, null, claims.secondFactor()));
	}

	/**
	 * Links the subject to an unexpired invitation with the same verified email. When two first requests race, one
	 * wins (optimistic locking and the unique subject index) and the other reads the result.
	 */
	private Optional<StaffMember> activateInvitation(TokenClaims claims) {

		if (!claims.emailVerified() || claims.email() == null) {
			return Optional.empty();
		}

		EmailAddress email;
		try {
			email = new EmailAddress(claims.email());
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}

		var invitation = staff.findByEmailAndStatus(email.value(), StaffStatus.INVITED)
				.filter(it -> !it.isInvitationExpired(clock.instant()));

		if (invitation.isPresent()) {
			try {
				commands.handle(new ActivateStaffMember(claims.subject(), email.value()));
			} catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException | StaffRuleViolation e) {
				// Another request activated it first, or it changed meanwhile: read whatever is there now
			}
		}

		// Also when no invitation was found: a concurrent first request may have just linked this subject
		return staff.findBySubject(claims.subject().value());
	}
}
