package com.samdev.appointments.customer.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.samdev.appointments.IntegrationTest;

/**
 * The demo customers load through the command handler, and loading them again changes nothing.
 */
@IntegrationTest
@ActiveProfiles("local")
class DemoCustomerLoaderTests {

	@Autowired DemoCustomerLoader loader;
	@Autowired JdbcTemplate jdbc;

	@Test
	void loadsDemoCustomersOnce() throws Exception {

		var before = demoCustomers();
		assertThat(before).isEqualTo(10);

		loader.run(null);

		assertThat(demoCustomers()).isEqualTo(before);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM customer.customer
				WHERE email = 'customer@appointments.test' AND identity_subject IS NULL AND status = 'ACTIVE'
				""", Long.class)).as("ready to be linked at first sign-up").isEqualTo(1);
	}

	private long demoCustomers() {
		return jdbc.queryForObject("""
				SELECT count(*) FROM customer.access_audit
				WHERE staff_subject = ? AND action = 'CREATE'
				""", Long.class, DemoCustomerLoader.LOADER);
	}
}
