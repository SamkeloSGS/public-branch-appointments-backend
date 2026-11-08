package com.samdev.appointments.identity.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.application.command.InviteStaffMember;
import com.samdev.appointments.identity.application.handler.StaffCommandHandler;
import com.samdev.appointments.identity.domain.exception.StaffRuleViolation;
import com.samdev.appointments.identity.domain.repository.StaffMembers;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

/**
 * At start-up, invites the configured first branch manager when no branch manager exists who is not removed.
 * Starting again, or a second instance starting at the same time, creates no second invitation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class FirstAdminInitializer implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(FirstAdminInitializer.class);

	private final IdentityProperties properties;
	private final StaffMembers staff;
	private final StaffCommandHandler commands;

	FirstAdminInitializer(IdentityProperties properties, StaffMembers staff, StaffCommandHandler commands) {
		this.properties = properties;
		this.staff = staff;
		this.commands = commands;
	}

	@Override
	public void run(ApplicationArguments args) {

		if (staff.existsByRoleAndStatusNot(StaffRole.BRANCH_MANAGER, StaffStatus.REMOVED)) {
			return;
		}

		var email = properties.firstAdminEmail();

		if (email == null || email.isBlank()) {
			LOG.warn("No branch manager exists and appointments.identity.first-admin-email is not set");
			return;
		}

		try {
			commands.handle(new InviteStaffMember(null, email, properties.firstAdminDisplayName(),
					StaffRole.BRANCH_MANAGER));
			LOG.info("Invited the first branch manager; they are activated at first sign-in with that email");
		} catch (StaffRuleViolation | DataIntegrityViolationException e) {
			LOG.info("The first branch manager was invited concurrently: {}", e.getMessage());
		}
	}
}
