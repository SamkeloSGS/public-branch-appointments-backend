package com.samdev.appointments.customer.infrastructure.demo;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.samdev.appointments.customer.application.command.RegisterCustomerByStaff;
import com.samdev.appointments.customer.application.handler.CustomerCommandHandler;
import com.samdev.appointments.customer.domain.exception.EmailInUseException;
import com.samdev.appointments.identity.Subject;

/**
 * Loads made-up staff-created customers from {@code demo/customers.json} when the {@code local} profile is active,
 * through the command handler (so the usual events and audit rows are written, by the staff subject
 * {@value #LOADER}). One of them has the email of the local Keycloak user {@code customer@appointments.test}, so
 * that user's first {@code POST /api/v1/customers/me} links it. Idempotent: customers whose email exists are skipped.
 */
@Component
@Profile("local")
class DemoCustomerLoader implements ApplicationRunner {

	static final String RESOURCE = "demo/customers.json";
	static final String LOADER = "demo-data-loader";

	private static final Logger LOG = LoggerFactory.getLogger(DemoCustomerLoader.class);

	private final JsonMapper json;
	private final CustomerCommandHandler commands;

	DemoCustomerLoader(JsonMapper json, CustomerCommandHandler commands) {
		this.json = json;
		this.commands = commands;
	}

	record DemoCustomer(String givenName, String familyName, String email, @Nullable String phone,
			@Nullable String language) {}

	record DemoCustomers(List<DemoCustomer> customers) {}

	@Override
	public void run(ApplicationArguments args) throws IOException {

		DemoCustomers data;
		try (var in = new ClassPathResource(RESOURCE).getInputStream()) {
			data = json.readValue(in, DemoCustomers.class);
		}

		var loaded = 0;

		for (var customer : data.customers()) {
			try {
				commands.handle(new RegisterCustomerByStaff(new Subject(LOADER), customer.givenName(),
						customer.familyName(), customer.email(), customer.phone(), customer.language()));
				loaded++;
			} catch (EmailInUseException e) {
				// Loaded on an earlier start
			}
		}

		LOG.info("Demo customers: {} new, {} already present", loaded, data.customers().size() - loaded);
	}
}
