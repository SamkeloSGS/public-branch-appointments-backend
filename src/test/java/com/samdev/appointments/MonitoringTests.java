package com.samdev.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Health is public without details; nothing else of the actuator is reachable over HTTP; the cross-module gauges
 * exist.
 */
@IntegrationTest
class MonitoringTests {

	@Autowired MockMvc mvc;
	@Autowired MeterRegistry metrics;

	@Test
	@DisplayName("OPS-1 health, liveness and readiness answer without a token and show component status only")
	void health() throws Exception {

		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"))
				.andExpect(jsonPath("$.components.db.details").doesNotExist());

		mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("OPS-1 no other actuator endpoint is reachable over HTTP")
	void nothingElseExposed() throws Exception {
		for (var path : new String[] { "/actuator/metrics", "/actuator/env", "/actuator/beans", "/actuator/modulith" }) {
			mvc.perform(get(path)).andExpect(status().is4xxClientError());
		}
	}

	@Test
	@DisplayName("OPS-2 gauges for the event registry, overbooked pool days and due notifications are registered")
	void gauges() {
		assertThat(metrics.find("modulith.events.incomplete").gauge()).isNotNull()
				.satisfies(it -> assertThat(it.value()).isGreaterThanOrEqualTo(0));
		assertThat(metrics.find("modulith.events.incomplete.oldest.age").gauge()).isNotNull();
		assertThat(metrics.find("booking.pool_days.overbooked").gauge()).isNotNull();
		assertThat(metrics.find("booking.appointments.rebooking_required").gauge()).isNotNull();
		assertThat(metrics.find("notification.due").gauge()).isNotNull()
				.satisfies(it -> assertThat(it.value()).isGreaterThanOrEqualTo(0));
	}
}
