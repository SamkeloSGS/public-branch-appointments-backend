package com.samdev.appointments.booking.infrastructure.metrics;

import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.application.port.BookingMetrics;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.vo.Actor;

/**
 * Counters for the booking commands, and two gauges read from the booking schema each time metrics are exported:
 * pool days from today on that are overbooked, and appointments waiting for a new time.
 */
@Component
class MicrometerBookingMetrics implements BookingMetrics {

	private final MeterRegistry registry;

	MicrometerBookingMetrics(MeterRegistry registry, JdbcTemplate jdbc) {

		this.registry = registry;

		Gauge.builder("booking.pool_days.overbooked", () -> count(jdbc,
				"SELECT count(*) FROM booking.pool_day_schedule WHERE overbooked AND schedule_date >= current_date"))
				.description("Pool days from today on with more reservations than capacity")
				.register(registry);

		Gauge.builder("booking.appointments.rebooking_required", () -> count(jdbc,
				"SELECT count(*) FROM booking.appointment WHERE status = 'REBOOKING_REQUIRED'"))
				.description("Appointments that lost their time and wait for the customer to choose a new one")
				.register(registry);
	}

	@Override
	public void booked(Actor actor) {
		Counter.builder("booking.appointments.booked")
				.description("New appointments")
				.tag("actor", label(actor))
				.register(registry)
				.increment();
	}

	@Override
	public void completed(Operation operation) {
		Counter.builder("booking.commands.completed")
				.description("Booking commands that succeeded")
				.tag("operation", label(operation))
				.register(registry)
				.increment();
	}

	@Override
	public void rejected(Operation operation, RejectionCode code) {
		Counter.builder("booking.commands.rejected")
				.description("Booking commands refused by a rule, with the code returned to the client")
				.tag("operation", label(operation))
				.tag("code", code.name())
				.register(registry)
				.increment();
	}

	@Override
	public void retried(Operation operation) {
		Counter.builder("booking.commands.retried")
				.description("Booking commands started again after a conflict with a concurrent request")
				.tag("operation", label(operation))
				.register(registry)
				.increment();
	}

	private static double count(JdbcTemplate jdbc, String sql) {
		var count = jdbc.queryForObject(sql, Long.class);
		return count == null ? 0 : count;
	}

	private static String label(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}
