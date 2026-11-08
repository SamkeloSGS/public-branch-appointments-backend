package com.samdev.appointments;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against PostgreSQL in Testcontainers: {@code mvn spring-boot:test-run}. Like the
 * tests, it runs without Vault, on the test secrets in {@code src/test/resources/application.properties}.
 */
public class TestAppointmentsApplication {

	public static void main(String[] args) {
		System.setProperty("spring.config.on-not-found", "ignore");
		SpringApplication.from(AppointmentsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}
}
