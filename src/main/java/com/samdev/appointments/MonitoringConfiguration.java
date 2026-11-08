package com.samdev.appointments;

import java.util.List;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.samdev.appointments.identity.PublicEndpoints;

/**
 * Monitoring that belongs to no single module. Metrics, traces and logs go to an OpenTelemetry collector over OTLP
 * (the Grafana stack in {@code compose.yaml} locally; see {@code management.*} in {@code application.yml}).
 * <ul>
 * <li>{@code /actuator/health} (with {@code /liveness} and {@code /readiness}) and {@code /actuator/info} are
 * public, without details. No other actuator endpoint is exposed over HTTP.</li>
 * <li>Gauges of the event publication registry: events whose listener has not completed yet, and the age of the
 * oldest, and those marked FAILED. A count that keeps growing means a listener keeps failing.</li>
 * <li>Log lines are also sent over OTLP, with the trace and span IDs of the request that wrote them
 * ({@code logback-spring.xml}).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class MonitoringConfiguration {

	@Bean
	PublicEndpoints monitoringEndpoints() {
		return () -> List.of(PublicEndpoints.Endpoint.get("/actuator/health"),
				PublicEndpoints.Endpoint.get("/actuator/health/**"), PublicEndpoints.Endpoint.get("/actuator/info"));
	}

	@Bean
	InitializingBean eventPublicationGauges(MeterRegistry registry, JdbcTemplate jdbc) {
		return () -> {
			Gauge.builder("modulith.events.incomplete", () -> number(jdbc,
					"SELECT count(*) FROM public.event_publication WHERE completion_date IS NULL"))
					.description("Published events whose listener has not completed yet")
					.register(registry);

			Gauge.builder("modulith.events.incomplete.oldest.age", () -> number(jdbc, """
					SELECT coalesce(extract(epoch FROM now() - min(publication_date)), 0) FROM public.event_publication
					WHERE completion_date IS NULL"""))
					.description("How long the oldest incomplete event has waited for its listener")
					.baseUnit("seconds")
					.register(registry);

			Gauge.builder("modulith.events.failed", () -> number(jdbc,
					"SELECT count(*) FROM public.event_publication WHERE status = 'FAILED'"))
					.description("Publications whose listener failed and that wait for a retry or a person")
					.register(registry);
		};
	}

	/**
	 * Connects the OTEL appender in {@code logback-spring.xml} to the OpenTelemetry SDK once it exists; lines logged
	 * before that are only written to the console.
	 */
	@Bean
	InitializingBean openTelemetryLogAppender(ObjectProvider<OpenTelemetry> openTelemetry) {
		return () -> openTelemetry.ifAvailable(OpenTelemetryAppender::install);
	}

	private static double number(JdbcTemplate jdbc, String sql) {
		var value = jdbc.queryForObject(sql, Double.class);
		return value == null ? 0 : value;
	}
}
