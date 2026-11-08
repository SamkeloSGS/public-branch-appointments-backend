package com.samdev.appointments.booking.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.application.port.IdempotencyKeys;
import com.samdev.appointments.customer.CustomerId;

/**
 * Idempotency keys in {@code booking.idempotency_key}, written in the booking transaction. Two requests with the same
 * key race on the primary key; the loser retries and finds the winner's appointment.
 */
@Component
class JdbcIdempotencyKeys implements IdempotencyKeys {

	private final JdbcTemplate jdbc;
	private final Clock clock;

	JdbcIdempotencyKeys(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public Optional<AppointmentId> find(CustomerId customerId, String key) {
		return jdbc.query("""
				SELECT appointment_id FROM booking.idempotency_key WHERE customer_id = ? AND idempotency_key = ?
				""", (rs, row) -> new AppointmentId(rs.getObject(1, UUID.class)), customerId.value(), key)
				.stream().findFirst();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void remember(CustomerId customerId, String key, AppointmentId appointmentId) {
		jdbc.update("""
				INSERT INTO booking.idempotency_key (customer_id, idempotency_key, appointment_id, created_at)
				VALUES (?, ?, ?, ?)
				""", customerId.value(), key, appointmentId.value(), Timestamp.from(clock.instant()));
	}

	@Override
	@Transactional
	public int purgeOlderThan(Instant cutoff) {
		return jdbc.update("DELETE FROM booking.idempotency_key WHERE created_at < ?", Timestamp.from(cutoff));
	}
}
