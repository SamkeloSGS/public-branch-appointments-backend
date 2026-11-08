package com.samdev.appointments.customer.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.application.port.AccessAudit;
import com.samdev.appointments.identity.Subject;

/**
 * Writes access-audit rows in the caller's transaction, so a failed write fails the request.
 */
@Component
class JdbcAccessAudit implements AccessAudit {

	private final JdbcTemplate jdbc;
	private final Clock clock;

	JdbcAccessAudit(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(Subject staff, Action action, @Nullable CustomerId customerId, @Nullable String searchQuery) {
		jdbc.update("""
				INSERT INTO customer.access_audit (id, customer_id, staff_subject, action, search_query, occurred_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), customerId == null ? null : customerId.value(), staff.value(), action.name(),
				searchQuery, Timestamp.from(clock.instant()));
	}
}
