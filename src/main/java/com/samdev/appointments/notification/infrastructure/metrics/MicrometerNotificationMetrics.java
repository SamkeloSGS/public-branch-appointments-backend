package com.samdev.appointments.notification.infrastructure.metrics;

import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.samdev.appointments.notification.application.port.NotificationMetrics;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.SendOutcome;
import com.samdev.appointments.notification.domain.vo.StatusReason;

/**
 * Counters for send attempts and skips, and gauges of the backlog read from the notification schema each time metrics
 * are exported: notifications due now, and how long the oldest of them has waited.
 */
@Component
class MicrometerNotificationMetrics implements NotificationMetrics {

	private final MeterRegistry registry;

	MicrometerNotificationMetrics(MeterRegistry registry, JdbcTemplate jdbc) {

		this.registry = registry;

		Gauge.builder("notification.due", () -> number(jdbc, """
				SELECT count(*) FROM notification.notification WHERE status = 'PENDING' AND send_after <= now()"""))
				.description("Notifications due and not yet sent")
				.register(registry);

		Gauge.builder("notification.due.oldest.age", () -> number(jdbc, """
				SELECT coalesce(extract(epoch FROM now() - min(send_after)), 0) FROM notification.notification
				WHERE status = 'PENDING' AND send_after <= now()"""))
				.description("How long the oldest due notification has waited")
				.baseUnit("seconds")
				.register(registry);
	}

	@Override
	public void attempted(Channel channel, SendOutcome.Kind outcome) {
		Counter.builder("notification.attempts")
				.description("Send attempts per channel and outcome")
				.tag("channel", label(channel))
				.tag("outcome", label(outcome))
				.register(registry)
				.increment();
	}

	@Override
	public void skipped(StatusReason reason) {
		Counter.builder("notification.skipped")
				.description("Notifications not sent because they no longer applied or could not reach the customer")
				.tag("reason", reason.name())
				.register(registry)
				.increment();
	}

	private static double number(JdbcTemplate jdbc, String sql) {
		var value = jdbc.queryForObject(sql, Double.class);
		return value == null ? 0 : value;
	}

	private static String label(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}
