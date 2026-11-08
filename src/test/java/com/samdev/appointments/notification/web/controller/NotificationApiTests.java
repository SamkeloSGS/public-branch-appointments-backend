package com.samdev.appointments.notification.web.controller;

import static com.samdev.appointments.identity.IdentityFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.BookingFixtures;
import com.samdev.appointments.booking.BookingFixtures.OpenService;
import com.samdev.appointments.booking.application.command.BookAppointment;
import com.samdev.appointments.booking.application.handler.AppointmentCommandHandler;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.identity.IdentityFixtures;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.notification.application.handler.NotificationDispatcher;
import com.samdev.appointments.notification.application.query.NotificationQueries;
import com.samdev.appointments.notification.domain.entity.Notification;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;

/**
 * Staff views, resend and provider callbacks: IDs and statuses only, access, signatures.
 */
@IntegrationTest
class NotificationApiTests {

	@Autowired MockMvc mvc;
	@Autowired IdentityFixtures identity;
	@Autowired BookingFixtures booking;
	@Autowired AppointmentCommandHandler appointments;
	@Autowired NotificationDispatcher dispatcher;
	@Autowired NotificationQueries queries;
	@Autowired CustomerDirectory customers;

	@Test
	@DisplayName("NT-12/NT-11 branch staff see an appointment's notifications without any personal data")
	void appointmentView() throws Exception {

		var branch = booking.openService(2);
		var customer = booking.customer();
		var email = customers.contactFor(customer).orElseThrow().email();
		var id = bookAndSend(branch, customer);
		var staff = token(identity.activeStaff(StaffRole.BRANCH_STAFF), 2);
		var customerCaller = token(IdentityFixtures.newAccount(), 2);

		mvc.perform(get("/api/v1/admin/appointments/{id}/notifications", id.value()).with(staff))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].type").value("CONFIRMATION"))
				.andExpect(jsonPath("$[0].status").value("SENT"))
				.andExpect(jsonPath("$[0].deliveries[0].channel").value("EMAIL"))
				.andExpect(jsonPath("$[0].deliveries[0].status").value("SENT"))
				.andExpect(content().string(not(containsString(email))))
				.andExpect(content().string(not(containsString("Test Customer"))));

		mvc.perform(get("/api/v1/admin/appointments/{id}/notifications", id.value()).with(customerCaller))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/v1/admin/notifications").param("status", "SENT").param("size", "100").with(staff))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].status", everyItem(is("SENT"))))
				.andExpect(jsonPath("$.items[?(@.appointmentId == '%s')]".formatted(id), hasSize(1)))
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	@DisplayName("NT-12 resending creates a new pending notification and leaves the original; pending ones are refused")
	void resend() throws Exception {

		var branch = booking.openService(2);
		var id = bookAndSend(branch, booking.customer());
		var staff = token(identity.activeStaff(StaffRole.BRANCH_STAFF), 2);
		var confirmation = confirmation(id);

		mvc.perform(post("/api/v1/admin/notifications/{id}/resend", confirmation.getId().value())
				.with(token(IdentityFixtures.newAccount(), 2)))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/v1/admin/notifications/{id}/resend", confirmation.getId().value()).with(staff))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("CONFIRMATION"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.deliveries", hasSize(0)));

		assertThat(queries.get(confirmation.getId()).getStatus()).hasToString("SENT");
		var copy = queries.forAppointment(id).stream()
				.filter(it -> it.getType() == NotificationType.CONFIRMATION && !it.getId().equals(confirmation.getId()))
				.findFirst().orElseThrow();

		mvc.perform(post("/api/v1/admin/notifications/{id}/resend", copy.getId().value()).with(staff))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"));

		dispatcher.dispatchDue();
		assertThat(queries.get(copy.getId()).getStatus()).hasToString("SENT");
	}

	@Test
	@DisplayName("NT-16 a signed callback updates the delivery; a bad signature is 401; an unknown message is ignored")
	void callbacks() throws Exception {

		var branch = booking.openService(2);
		var id = bookAndSend(branch, booking.customer());
		var messageId = confirmation(id).getDeliveries().getFirst().getProviderMessageId();
		var body = "{\"messageId\":\"%s\",\"status\":\"DELIVERED\"}".formatted(messageId);

		mvc.perform(post("/api/v1/webhooks/notifications/local").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-Signature", "0".repeat(64))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/webhooks/notifications/local").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/webhooks/notifications/unknown").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-Signature", sign(body))).andExpect(status().isNotFound());

		mvc.perform(post("/api/v1/webhooks/notifications/local").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-Signature", sign(body))).andExpect(status().isOk());
		assertThat(confirmation(id).getDeliveries().getFirst().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

		var unknown = "{\"messageId\":\"nobody\",\"status\":\"BOUNCED\"}";
		mvc.perform(post("/api/v1/webhooks/notifications/local").contentType(MediaType.APPLICATION_JSON).content(unknown)
				.header("X-Signature", sign(unknown))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("NT-12 customers have no notification endpoints")
	void customersAreRefused() throws Exception {
		mvc.perform(get("/api/v1/admin/notifications").with(token(IdentityFixtures.newAccount(), 2)))
				.andExpect(status().isForbidden());
	}

	private AppointmentId bookAndSend(OpenService branch, com.samdev.appointments.customer.CustomerId customer) {

		var id = appointments.handle(new BookAppointment(customer, branch.serviceId(), branch.at("09:00"),
				Actor.STAFF, null)).appointmentId();

		await().atMost(Duration.ofSeconds(15)).until(() -> queries.forAppointment(id).stream()
				.anyMatch(it -> it.getType() == NotificationType.CONFIRMATION));
		dispatcher.dispatchDue();

		return id;
	}

	private Notification confirmation(AppointmentId id) {
		return queries.forAppointment(id).getFirst();
	}

	private static String sign(String body) {
		// webhook-secret-local in src/test/resources/application.properties
		return DeliveryCallbackController.sign("test-webhook-secret", body);
	}
}
