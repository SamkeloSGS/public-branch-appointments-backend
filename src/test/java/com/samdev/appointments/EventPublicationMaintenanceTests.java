package com.samdev.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retry and clean-up of the event publication registry, with a listener that fails a given number of times. The
 * scheduled job is off in tests ({@code appointments.events.maintenance-enabled=false}); the test runs the maintenance
 * and moves publication dates back instead of waiting.
 */
@IntegrationTest
@Import(EventPublicationMaintenanceTests.FlakyListener.class)
class EventPublicationMaintenanceTests {

	@Autowired EventPublicationMaintenance maintenance;
	@Autowired FlakyListener listener;
	@Autowired ApplicationEventPublisher events;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;

	@Test
	@DisplayName("OPS-4 a failed listener is retried once it is a retry interval old, and then completes")
	void retriesFailed() {

		var id = publish(1);
		awaitStatus(id, "FAILED");

		maintenance.retryFailed();
		assertThat(listener.handled(id)).as("too recent to retry").isFalse();

		age(id, Duration.ofMinutes(10));
		maintenance.retryFailed();

		awaitStatus(id, "COMPLETED");
		assertThat(listener.handled(id)).isTrue();
		assertThat(attempts(id)).isEqualTo(2);
	}

	@Test
	@DisplayName("OPS-4 a publication out of attempts stays FAILED for a person to look at")
	void stopsAfterMaxAttempts() {

		var id = publish(100);
		awaitStatus(id, "FAILED");
		age(id, Duration.ofMinutes(10));
		jdbc.update("UPDATE public.event_publication SET completion_attempts = 12 WHERE serialized_event LIKE ?",
				"%" + id + "%");

		maintenance.retryFailed();

		assertThat(status(id)).isEqualTo("FAILED");
		assertThat(attempts(id)).isEqualTo(12);
	}

	@Test
	@DisplayName("OPS-4 completed publications are deleted after the retention period; recent ones are kept")
	void deletesOldCompleted() {

		var old = publish(0);
		var recent = publish(0);
		awaitStatus(old, "COMPLETED");
		awaitStatus(recent, "COMPLETED");
		jdbc.update("UPDATE public.event_publication SET completion_date = now() - interval '8 days' "
				+ "WHERE serialized_event LIKE ?", "%" + old + "%");

		maintenance.deleteCompleted();

		assertThat(count(old)).isZero();
		assertThat(count(recent)).isEqualTo(1);
	}

	/**
	 * Publishes an event in a transaction, as aggregates do; its listener throws the given number of times.
	 */
	private UUID publish(int failures) {

		var id = UUID.randomUUID();
		listener.failTimes(id, failures);
		new TransactionTemplate(transactions).executeWithoutResult(it -> events.publishEvent(new MaintenanceProbe(id)));

		return id;
	}

	private void age(UUID id, Duration age) {
		jdbc.update("UPDATE public.event_publication SET publication_date = now() - make_interval(secs => ?) "
				+ "WHERE serialized_event LIKE ?", (double) age.toSeconds(), "%" + id + "%");
	}

	private void awaitStatus(UUID id, String status) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(status(id)).isEqualTo(status));
	}

	private String status(UUID id) {
		return jdbc.queryForObject("SELECT status FROM public.event_publication WHERE serialized_event LIKE ?",
				String.class, "%" + id + "%");
	}

	private int attempts(UUID id) {
		return jdbc.queryForObject("""
				SELECT completion_attempts FROM public.event_publication WHERE serialized_event LIKE ?""",
				Integer.class, "%" + id + "%");
	}

	private int count(UUID id) {
		return jdbc.queryForObject("SELECT count(*) FROM public.event_publication WHERE serialized_event LIKE ?",
				Integer.class, "%" + id + "%");
	}

	record MaintenanceProbe(UUID id) {}

	/**
	 * Throws while failures are left for an event's ID, then records it as handled. Used through methods only: the
	 * bean is a proxy (the listener is asynchronous and transactional).
	 */
	static class FlakyListener {

		private final Map<UUID, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
		private final Set<UUID> handled = ConcurrentHashMap.newKeySet();

		public void failTimes(UUID id, int failures) {
			failuresLeft.put(id, new AtomicInteger(failures));
		}

		public boolean handled(UUID id) {
			return handled.contains(id);
		}

		@ApplicationModuleListener
		void on(MaintenanceProbe event) {

			var left = failuresLeft.get(event.id());
			if (left != null && left.getAndDecrement() > 0) {
				throw new IllegalStateException("Failing on purpose");
			}
			handled.add(event.id());
		}
	}
}
