package com.samdev.appointments.branchmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.samdev.appointments.SharedPersistenceConfiguration;
import com.samdev.appointments.TestcontainersConfiguration;
import com.samdev.appointments.branchmanagement.application.command.DefineCapacityPool;
import com.samdev.appointments.branchmanagement.application.handler.CapacityPoolCommandHandler;
import com.samdev.appointments.branchmanagement.domain.repository.CapacityPools;

/**
 * BM-10: events are published in the same transaction as the state change and delivered through the event
 * publication registry.
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, SharedPersistenceConfiguration.class,
		EventDeliveryTests.ListenerConfiguration.class })
class EventDeliveryTests {

	static final String FAILING = "Failing pool";

	@Autowired CapacityPools pools;
	@Autowired CapacityPoolCommandHandler commands;
	@Autowired RecordingListener recorder;
	@Autowired TransactionTemplate transactions;
	@Autowired JdbcTemplate jdbc;

	@Test
	@DisplayName("BM-10 a delivered event's publication is marked complete")
	void completesPublication() {

		var name = ModuleFixtures.uniqueName("Pool");
		var id = define(name);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(recorder.names()).contains(name);
			assertThat(publications(name, RecordingListener.class, true)).isEqualTo(1);
		});

		assertThat(pools.findById(id)).isPresent();
	}

	@Test
	@DisplayName("BM-10 a failing listener leaves the state committed and the publication incomplete")
	void failingListenerKeepsStateAndIncompletePublication() {

		var name = ModuleFixtures.uniqueName(FAILING);
		var id = define(name);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(FailingListener.attempts).contains(name);
			assertThat(publications(name, FailingListener.class, false)).isEqualTo(1);
		});

		assertThat(pools.findById(id)).isPresent();
		assertThat(publications(name, FailingListener.class, true)).isZero();
	}

	@Test
	@DisplayName("BM-10 if the transaction rolls back, no event is delivered")
	void rollbackDeliversNothing() throws InterruptedException {

		var name = ModuleFixtures.uniqueName("Pool");

		var id = transactions.execute(status -> {
			var poolId = define(name);
			status.setRollbackOnly();
			return poolId;
		});

		Thread.sleep(500);

		assertThat(pools.findById(id)).isEmpty();
		assertThat(recorder.names()).doesNotContain(name);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM public.event_publication WHERE serialized_event LIKE ?",
				Integer.class, "%" + name + "%")).isZero();
	}

	private CapacityPoolId define(String name) {
		return commands.handle(new DefineCapacityPool(name, 1));
	}

	private int publications(String name, Class<?> listener, boolean completed) {
		return jdbc.queryForObject("""
				SELECT count(*) FROM public.event_publication
				WHERE serialized_event LIKE ? AND listener_id LIKE ? AND (completion_date IS NOT NULL) = ?
				""", Integer.class, "%" + name + "%", "%" + listener.getSimpleName() + "%", completed);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ListenerConfiguration {

		@Bean
		RecordingListener recordingListener() {
			return new RecordingListener();
		}

		@Bean
		FailingListener failingListener() {
			return new FailingListener();
		}
	}

	static class RecordingListener {

		private final List<String> names = new CopyOnWriteArrayList<>();

		// Accessed through a method: the bean is a proxy because of @ApplicationModuleListener
		List<String> names() {
			return names;
		}

		@ApplicationModuleListener
		void on(CapacityPoolDefined event) {
			names.add(event.name());
		}
	}

	static class FailingListener {

		static final List<String> attempts = new CopyOnWriteArrayList<>();

		@ApplicationModuleListener
		void on(CapacityPoolDefined event) {
			if (event.name().startsWith(FAILING)) {
				attempts.add(event.name());
				throw new IllegalStateException("Simulated consumer failure");
			}
		}
	}
}
