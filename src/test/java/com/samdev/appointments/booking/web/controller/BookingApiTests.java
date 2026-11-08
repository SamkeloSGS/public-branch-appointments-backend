package com.samdev.appointments.booking.web.controller;

import static com.samdev.appointments.booking.BookingFixtures.tomorrow;
import static com.samdev.appointments.identity.IdentityFixtures.token;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.booking.BookingFixtures;
import com.samdev.appointments.booking.BookingFixtures.OpenService;
import com.samdev.appointments.branchmanagement.ModuleFixtures;
import com.samdev.appointments.identity.IdentityFixtures;
import com.samdev.appointments.identity.StaffRole;

/**
 * Availability, customer self-service by reference, and staff endpoints: status codes, rule codes, ETag, If-Match,
 * Idempotency-Key, roles and branch scoping.
 */
@IntegrationTest
class BookingApiTests {

	@Autowired MockMvc mvc;
	@Autowired BookingFixtures fixtures;
	@Autowired IdentityFixtures identity;

	// Availability

	@Test
	@DisplayName("BK-1 availability is public: open times in order, an empty list on a closed day, 404 when not bookable")
	void availability() throws Exception {

		var branch = fixtures.openService(1);

		mvc.perform(get("/api/v1/availability")
						.param("serviceId", branch.serviceId().toString()).param("date", tomorrow().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openTimes", hasSize(18)))
				.andExpect(jsonPath("$.openTimes[0]").value("08:00:00"))
				.andExpect(jsonPath("$.openTimes[17]").value("16:30:00"));

		mvc.perform(get("/api/v1/availability")
						.param("serviceId", UUID.randomUUID().toString()).param("date", tomorrow().toString()))
				.andExpect(status().isNotFound());

		mvc.perform(get("/api/v1/availability")
						.param("serviceId", branch.serviceId().toString()).param("date", tomorrow().minusDays(5).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openTimes", hasSize(0)));

		mvc.perform(get("/api/v1/availability/days")
						.param("serviceId", branch.serviceId().toString()).param("from", tomorrow().toString())
						.param("to", tomorrow().plusDays(6).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dates", hasSize(7)));

		mvc.perform(get("/api/v1/availability/days")
						.param("serviceId", branch.serviceId().toString()).param("from", tomorrow().toString())
						.param("to", tomorrow().plusDays(31).toString()))
				.andExpect(status().isBadRequest());
	}

	// Customer

	@Test
	@DisplayName("BK-2/BK-17/BK-16 a customer books with an idempotency key and reaches only their own references")
	void customerBooking() throws Exception {

		var branch = fixtures.openService(2);
		var account = IdentityFixtures.newAccount();
		var caller = token(account, 1);

		mvc.perform(json(post("/api/v1/appointments"), booking(branch, "09:00")).with(caller))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));

		createProfile(caller);

		var body = mvc.perform(json(post("/api/v1/appointments"), booking(branch, "09:00"))
						.header("Idempotency-Key", "k1").with(caller))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
				.andExpect(jsonPath("$.status").value("BOOKED"))
				.andReturn().getResponse().getContentAsString();
		String reference = JsonPath.read(body, "$.reference");

		mvc.perform(json(post("/api/v1/appointments"), booking(branch, "09:00")).header("Idempotency-Key", "k1")
						.with(caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reference").value(reference));

		mvc.perform(get("/api/v1/appointments/mine").with(caller))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].reference").value(reference))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.totalPages").value(1));
		mvc.perform(get("/api/v1/appointments/mine").param("filter", "past").with(caller))
				.andExpect(jsonPath("$.items", hasSize(0)))
				.andExpect(jsonPath("$.totalPages").value(0));

		var stranger = token(IdentityFixtures.newAccount(), 1);
		createProfile(stranger);
		mvc.perform(get("/api/v1/appointments/mine/{reference}", reference).with(stranger))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/appointments/mine/{reference}/cancel", reference).header(HttpHeaders.IF_MATCH, "*")
				.with(stranger)).andExpect(status().isNotFound());

		mvc.perform(json(post("/api/v1/appointments/mine/{reference}/reschedule", reference), start("11:00"))
				.with(caller)).andExpect(status().isPreconditionRequired());
		mvc.perform(json(post("/api/v1/appointments/mine/{reference}/reschedule", reference), start("11:00"))
						.header(HttpHeaders.IF_MATCH, "\"0\"").with(caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startTime").value("11:00:00"))
				.andExpect(jsonPath("$.reference").value(reference));
		mvc.perform(post("/api/v1/appointments/mine/{reference}/cancel", reference.toLowerCase())
						.header(HttpHeaders.IF_MATCH, "\"0\"").with(caller))
				.andExpect(status().isPreconditionFailed());
		mvc.perform(post("/api/v1/appointments/mine/{reference}/cancel", reference).header(HttpHeaders.IF_MATCH, "\"1\"")
						.with(caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.closedReason").value("CUSTOMER_CANCELLED"));
	}

	@Test
	@DisplayName("BK-4 rejections are 409 with the code; a missing token is 401")
	void rejections() throws Exception {

		var branch = fixtures.openService(1);
		var caller = token(IdentityFixtures.newAccount(), 1);
		createProfile(caller);

		mvc.perform(json(post("/api/v1/appointments"), booking(branch, "09:15")).with(caller))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OUTSIDE_BOOKING_WINDOW"));
		mvc.perform(json(post("/api/v1/appointments"), booking(branch, "07:00")).with(caller))
				.andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
		mvc.perform(json(post("/api/v1/appointments"), booking(branch, "09:00")))
				.andExpect(status().isUnauthorized());
	}

	// Staff

	@Test
	@DisplayName("BK-14/BK-16 branch staff book for a customer and see the day list; customers may not")
	void staff() throws Exception {

		var branch = fixtures.openService(2);
		var staff = token(identity.activeStaff(StaffRole.BRANCH_STAFF), 2);
		var customerCaller = token(IdentityFixtures.newAccount(), 2);
		var customer = fixtures.customer();

		var body = mvc.perform(json(post("/api/v1/admin/appointments"),
						"{\"customerId\":\"%s\",\"serviceId\":\"%s\",\"start\":\"%sT10:00\"}".formatted(customer,
								branch.serviceId(), tomorrow()))
						.with(staff))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookedBy").value("STAFF"))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		String reference = JsonPath.read(body, "$.reference");

		mvc.perform(get("/api/v1/admin/appointments").param("date", tomorrow().toString()).with(staff))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.appointment.id == '%s')].customerName".formatted(id))
						.value("Test Customer"));

		mvc.perform(get("/api/v1/admin/appointments").param("reference", reference).with(staff))
				.andExpect(jsonPath("$.id").value(id));

		mvc.perform(get("/api/v1/admin/appointments").param("date", tomorrow().toString()).with(customerCaller))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/appointments/{id}", id).with(customerCaller)).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/v1/admin/appointments/{id}/reschedule", id), "{}").header(HttpHeaders.IF_MATCH, "*")
				.with(customerCaller)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/appointments").param("reference", "ZZZZZZZZ").with(staff))
				.andExpect(status().isNotFound());

		mvc.perform(post("/api/v1/admin/appointments/{id}/check-in", id).header(HttpHeaders.IF_MATCH, "\"0\"")
						.with(staff))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		mvc.perform(post("/api/v1/admin/appointments/{id}/displace", id).header(HttpHeaders.IF_MATCH, "*").with(staff))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/appointments/{id}/cancel", id).header(HttpHeaders.IF_MATCH, "\"0\"").with(staff))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.closedReason").value("STAFF_CANCELLED"));
	}

	@Test
	@DisplayName("BK-16/BK-11/BK-12 the impact preview and overbooked list need the branch manager")
	void branchManager() throws Exception {

		var branch = fixtures.openService(2).on(ModuleFixtures.uniqueDate());
		var manager = token(identity.activeStaff(StaffRole.BRANCH_MANAGER), 2);
		var staff = token(identity.activeStaff(StaffRole.BRANCH_STAFF), 2);
		var path = "/api/v1/admin/schedule-impact";
		var closure = "{\"kind\":\"CALENDAR_EXCEPTION\",\"date\":\"%s\",\"exceptionType\":\"CLOSED\"}"
				.formatted(branch.date());

		staffBooks(branch, staff, "09:00");

		mvc.perform(json(post(path), closure).with(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displacedAppointments").value(1))
				.andExpect(jsonPath("$.overbookedIntervals").value(0));
		mvc.perform(json(post(path), "{\"kind\":\"CAPACITY_OVERRIDE\"}").with(manager))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post(path), closure).with(staff))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/v1/admin/overbooked").param("from", branch.date().toString())
						.param("to", branch.date().plusDays(7).toString()).with(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.poolId == '%s')]".formatted(branch.poolId()), hasSize(0)));
	}

	private void staffBooks(OpenService branch, RequestPostProcessor staff, String time) throws Exception {
		mvc.perform(json(post("/api/v1/admin/appointments"),
				"{\"customerId\":\"%s\",\"serviceId\":\"%s\",\"start\":\"%sT%s\"}".formatted(fixtures.customer(),
						branch.serviceId(), branch.date(), time))
				.with(staff)).andExpect(status().isCreated());
	}

	private void createProfile(RequestPostProcessor caller) throws Exception {
		mvc.perform(json(post("/api/v1/customers/me"), "{\"givenName\":\"Thandi\",\"familyName\":\"Nkosi\"}").with(caller))
				.andExpect(status().isCreated());
	}

	private static String booking(OpenService branch, String time) {
		return "{\"serviceId\":\"%s\",\"start\":\"%sT%s\"}".formatted(branch.serviceId(), branch.date(), time);
	}

	private static String start(String time) {
		return "{\"start\":\"%sT%s\"}".formatted(tomorrow(), time);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}
}
