package com.samdev.appointments.branchmanagement.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ModuleFixtures;
import com.samdev.appointments.identity.IdentityFixtures;

/**
 * Admin endpoints: status codes, Problem Details, ETag and If-Match.
 */
@IntegrationTest
class AdminApiTests {

	/**
	 * {@link ModuleFixtures#EVERY_DAY} as a request body.
	 */
	static final String STANDARD_HOURS = """
			{ "days": {
			  "MONDAY": [ { "start": "08:00", "end": "17:00" } ], "TUESDAY": [ { "start": "08:00", "end": "17:00" } ],
			  "WEDNESDAY": [ { "start": "08:00", "end": "17:00" } ], "THURSDAY": [ { "start": "08:00", "end": "17:00" } ],
			  "FRIDAY": [ { "start": "08:00", "end": "17:00" } ], "SATURDAY": [ { "start": "08:00", "end": "17:00" } ],
			  "SUNDAY": [ { "start": "08:00", "end": "17:00" } ] } }
			""";

	@Autowired MockMvc mvc;
	@Autowired IdentityFixtures identity;
	Callers callers;
	@Autowired ModuleFixtures fixtures;

	static final LocalDate TOMORROW = LocalDate.now(ModuleFixtures.ZONE).plusDays(1);

	@BeforeEach
	void callers() {
		callers = new Callers(identity);
	}

	// BM-1, BM-2

	@Test
	@DisplayName("BM-1 the branch is read from configuration, with the version of its hours as the ETag")
	void readsTheBranch() throws Exception {

		mvc.perform(get("/api/v1/admin/branch").with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.ETAG))
				.andExpect(jsonPath("$.code").value("MAIN"))
				.andExpect(jsonPath("$.segments", hasSize(2)))
				.andExpect(jsonPath("$.address.city").value("Cape Town"));
	}

	@Test
	@DisplayName("BM-2 replacing weekly hours returns the schedule and a new ETag; overlaps return 400")
	void replacesWeeklyHours() throws Exception {

		var etag = etagOfBranch();
		var version = Long.parseLong(etag.replace("\"", ""));

		try {
			mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), """
					{ "days": { "MONDAY": [ { "start": "08:00", "end": "12:00" }, { "start": "13:00", "end": "17:00" } ] } }
					""").header(HttpHeaders.IF_MATCH, etag).with(callers.manager()))
					.andExpect(status().isOk())
					.andExpect(header().string(HttpHeaders.ETAG, "\"" + (version + 1) + "\""))
					.andExpect(jsonPath("$.weeklyHours.MONDAY", hasSize(2)))
					.andExpect(jsonPath("$.weeklyHours.SUNDAY", hasSize(0)));

			mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), """
					{ "days": { "MONDAY": [ { "start": "08:00", "end": "12:00" }, { "start": "11:00", "end": "13:00" } ] } }
					""").header(HttpHeaders.IF_MATCH, "*").with(callers.manager()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.detail", containsString("overlap")));

			mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), """
					{ "days": { "MONDAY": [ { "start": "12:00", "end": "08:00" } ] } }
					""").header(HttpHeaders.IF_MATCH, "*").with(callers.manager()))
					.andExpect(status().isBadRequest());

			mvc.perform(get("/api/v1/admin/branch").with(callers.manager()))
					.andExpect(header().string(HttpHeaders.ETAG, "\"" + (version + 1) + "\""))
					.andExpect(jsonPath("$.weeklyHours.MONDAY", hasSize(2)));
		} finally {
			fixtures.standardHours();
		}
	}

	// BM-4, BM-5, BM-6

	@Test
	@DisplayName("BM-4/BM-6 offering a retired service returns 409")
	void retiredServiceCannotBeOffered() throws Exception {

		var serviceId = fixtures.service();
		var poolId = fixtures.pool();

		mvc.perform(post("/api/v1/admin/services/{id}/retire", serviceId.value()).header(HttpHeaders.IF_MATCH, "\"0\"")
				.with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mvc.perform(json(post("/api/v1/admin/offerings"), offering(serviceId.value(), poolId.value()))
				.with(callers.manager()))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("BM-13 the branch manager manages service categories; names are unique per segment, the segment is fixed")
	void serviceCategories() throws Exception {

		var name = ModuleFixtures.uniqueName("Loans");
		var body = "{ \"name\": \"%s\", \"segment\": \"PERSONAL\", \"description\": \"Home loans\" }".formatted(name);

		var location = mvc.perform(json(post("/api/v1/admin/service-categories"), body).with(callers.manager()))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
				.andExpect(jsonPath("$.segment").value("PERSONAL"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mvc.perform(json(post("/api/v1/admin/service-categories"), body).with(callers.manager()))
				.andExpect(status().isConflict());

		mvc.perform(json(post("/api/v1/admin/service-categories"), "{ \"name\": \"X\", \"segment\": \"RETAIL\" }")
				.with(callers.manager()))
				.andExpect(status().isBadRequest());

		mvc.perform(json(put(location), "{ \"name\": \"%s and credit\" }".formatted(name))
				.header(HttpHeaders.IF_MATCH, "\"0\"").with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(name + " and credit"))
				.andExpect(jsonPath("$.segment").value("PERSONAL"));

		mvc.perform(post(location + "/retire").header(HttpHeaders.IF_MATCH, "\"1\"").with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mvc.perform(get("/api/v1/admin/service-categories").param("segment", "PERSONAL").with(callers.manager()))
				.andExpect(jsonPath("$[*].segment", everyItem(is("PERSONAL"))));

		mvc.perform(get("/api/v1/admin/service-categories").with(callers.staff()))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("BM-13 a service is defined in an existing category and moves only within its segment")
	void servicesInCategories() throws Exception {

		var personal = fixtures.category(CustomerSegment.PERSONAL);
		var otherPersonal = fixtures.category(CustomerSegment.PERSONAL);
		var business = fixtures.category(CustomerSegment.BUSINESS);

		var location = mvc.perform(json(post("/api/v1/admin/services"), service(personal.value())).with(callers.manager()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.categoryId").value(personal.toString()))
				.andExpect(jsonPath("$.segment").value("PERSONAL"))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mvc.perform(json(put(location + "/category"), "{ \"categoryId\": \"%s\" }".formatted(otherPersonal))
				.header(HttpHeaders.IF_MATCH, "\"0\"").with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categoryId").value(otherPersonal.toString()))
				.andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

		mvc.perform(json(put(location + "/category"), "{ \"categoryId\": \"%s\" }".formatted(business))
				.header(HttpHeaders.IF_MATCH, "\"1\"").with(callers.manager()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail", containsString("BUSINESS")));

		mvc.perform(json(post("/api/v1/admin/services"), service(UUID.randomUUID())).with(callers.manager()))
				.andExpect(status().isNotFound());

		mvc.perform(json(post("/api/v1/admin/services"), "{ \"name\": \"No category\", \"defaultDurationMinutes\": 20 }")
				.with(callers.manager()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("BM-4 a service is defined with prerequisites; its description and prerequisites can be replaced")
	void serviceDetails() throws Exception {

		var body = """
				{ "name": "%s", "categoryId": "%s", "defaultDurationMinutes": 30,
				  "prerequisites": [ "Your ID document" ] }
				""".formatted(ModuleFixtures.uniqueName("Service"), fixtures.category(CustomerSegment.PERSONAL));

		var location = mvc.perform(json(post("/api/v1/admin/services"), body).with(callers.manager()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prerequisites[0]").value("Your ID document"))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mvc.perform(json(put(location), """
				{ "description": "Open an account", "prerequisites": [ "Your ID document", "Proof of address" ] }
				""").header(HttpHeaders.IF_MATCH, "\"0\"").with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
				.andExpect(jsonPath("$.description").value("Open an account"))
				.andExpect(jsonPath("$.prerequisites", hasSize(2)));

		mvc.perform(json(put(location), "{ \"prerequisites\": [ \"%s\" ] }".formatted("x".repeat(201)))
				.header(HttpHeaders.IF_MATCH, "\"1\"").with(callers.manager()))
				.andExpect(status().isBadRequest());

		mvc.perform(json(put(location), "{}").header(HttpHeaders.IF_MATCH, "*").with(callers.staff()))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("BM-5 pools: duplicate name 409, base capacity below 1 is 400")
	void capacityPools() throws Exception {

		var name = ModuleFixtures.uniqueName("Consultants");
		var path = "/api/v1/admin/capacity-pools";

		var location = mvc.perform(json(post(path), "{ \"name\": \"%s\", \"baseCapacity\": 2 }".formatted(name))
				.with(callers.manager()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.baseCapacity").value(2))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mvc.perform(json(post(path), "{ \"name\": \"%s\", \"baseCapacity\": 2 }".formatted(name))
				.with(callers.manager()))
				.andExpect(status().isConflict());

		mvc.perform(json(post(path), "{ \"name\": \"Tellers\", \"baseCapacity\": 0 }").with(callers.manager()))
				.andExpect(status().isBadRequest());

		mvc.perform(json(patch(location), "{ \"baseCapacity\": 4 }").header(HttpHeaders.IF_MATCH, "\"0\"")
				.with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.baseCapacity").value(4));

		mvc.perform(get(path).with(callers.manager()))
				.andExpect(jsonPath("$[*].name", hasItem(name)));
	}

	@Test
	@DisplayName("BM-6 offerings: create, duplicate 409, change, withdraw")
	void offerings() throws Exception {

		var serviceId = fixtures.service();
		var poolId = fixtures.pool();
		var path = "/api/v1/admin/offerings";

		var location = mvc.perform(json(post(path), offering(serviceId.value(), poolId.value()))
				.with(callers.manager()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.durationMinutes").value(30))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mvc.perform(json(post(path), offering(serviceId.value(), poolId.value())).with(callers.manager()))
				.andExpect(status().isConflict());

		mvc.perform(json(patch(location), "{ \"durationMinutes\": 45 }").header(HttpHeaders.IF_MATCH, "\"0\"")
				.with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.durationMinutes").value(45))
				.andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

		mvc.perform(json(patch(location), "{ \"durationMinutes\": 50 }").header(HttpHeaders.IF_MATCH, "\"1\"")
				.with(callers.manager()))
				.andExpect(status().isBadRequest());

		mvc.perform(delete(location).header(HttpHeaders.IF_MATCH, "\"1\"").with(callers.manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mvc.perform(json(post(path), offering(fixtures.service().value(), UUID.randomUUID())).with(callers.manager()))
				.andExpect(status().isNotFound());
	}

	// BM-7, BM-8

	@Test
	@DisplayName("BM-7 calendar exceptions: 201, duplicate date 409, past date 400, special hours need ranges")
	void calendarExceptions() throws Exception {

		var date = ModuleFixtures.uniqueDate();
		var path = "/api/v1/admin/calendar-exceptions";

		var location = mvc.perform(json(post(path),
				"{ \"date\": \"%s\", \"type\": \"CLOSED\", \"reason\": \"Holiday\" }".formatted(date))
				.with(callers.manager()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mvc.perform(json(post(path), "{ \"date\": \"%s\", \"type\": \"CLOSED\" }".formatted(date))
				.with(callers.manager()))
				.andExpect(status().isConflict());

		mvc.perform(json(post(path), "{ \"date\": \"%s\", \"type\": \"CLOSED\" }".formatted(TOMORROW.minusDays(2)))
				.with(callers.manager()))
				.andExpect(status().isBadRequest());

		mvc.perform(json(post(path),
				"{ \"date\": \"%s\", \"type\": \"SPECIAL_HOURS\" }".formatted(ModuleFixtures.uniqueDate()))
				.with(callers.manager()))
				.andExpect(status().isBadRequest());

		mvc.perform(get(path).with(callers.manager()))
				.andExpect(jsonPath("$[*].date", hasItem(date.toString())));

		mvc.perform(delete(location).with(callers.manager()))
				.andExpect(status().isNoContent());

		mvc.perform(get(path).with(callers.manager()))
				.andExpect(jsonPath("$[*].date", not(hasItem(date.toString()))));
	}

	@Test
	@DisplayName("BM-8 capacity overrides: above base accepted, overlapping window 409, range query, removal")
	void capacityOverrides() throws Exception {

		var poolId = fixtures.pool();
		var path = "/api/v1/admin/capacity-overrides";

		var location = mvc.perform(json(post(path), override(poolId.value(), "09:00", "12:00", 10))
				.with(callers.manager()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.capacity").value(10))
				.andExpect(jsonPath("$.window.start").value("09:00:00"))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mvc.perform(json(post(path), override(poolId.value(), "11:00", "13:00", 1)).with(callers.manager()))
				.andExpect(status().isConflict());

		var ofPool = "$[?(@.poolId == '%s')]".formatted(poolId);

		mvc.perform(get(path).param("from", TOMORROW.toString()).param("to", TOMORROW.toString())
				.with(callers.manager()))
				.andExpect(jsonPath(ofPool, hasSize(1)));

		mvc.perform(get(path).param("from", TOMORROW.plusDays(1).toString()).with(callers.manager()))
				.andExpect(jsonPath(ofPool, hasSize(0)));

		mvc.perform(delete(location).with(callers.manager()))
				.andExpect(status().isNoContent());
	}

	// BM-12

	@Test
	@DisplayName("BM-12 the second update from the same version receives 412; a missing If-Match receives 428")
	void optimisticConcurrency() throws Exception {

		var etag = etagOfBranch();

		mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), STANDARD_HOURS).header(HttpHeaders.IF_MATCH, etag)
				.with(callers.manager()))
				.andExpect(status().isOk());

		mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), STANDARD_HOURS).header(HttpHeaders.IF_MATCH, etag)
				.with(callers.manager()))
				.andExpect(status().isPreconditionFailed())
				.andExpect(jsonPath("$.status").value(412));

		mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), STANDARD_HOURS).with(callers.manager()))
				.andExpect(status().isPreconditionRequired());
	}

	@Test
	@DisplayName("BM-12 updates to different aggregates do not conflict")
	void differentAggregatesDoNotConflict() throws Exception {

		var poolId = fixtures.pool();
		var first = fixtures.offering(fixtures.service(), poolId);
		var second = fixtures.offering(fixtures.service(), poolId);

		for (var offering : new Object[] { first.value(), second.value() }) {
			mvc.perform(json(patch("/api/v1/admin/offerings/{offeringId}", offering), "{ \"maxAdvanceDays\": 60 }")
					.header(HttpHeaders.IF_MATCH, "\"0\"").with(callers.manager()))
					.andExpect(status().isOk());
		}
	}

	@Test
	void unknownPoolReturns404() throws Exception {
		mvc.perform(get("/api/v1/admin/capacity-pools/{id}", UUID.randomUUID()).with(callers.manager()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Not found"));
	}

	private String etagOfBranch() throws Exception {
		return mvc.perform(get("/api/v1/admin/branch").with(callers.manager()))
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
	}

	static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	static String offering(Object serviceId, Object poolId) {
		return """
				{ "serviceId": "%s", "poolId": "%s", "slotIntervalMinutes": 15, "minLeadTimeMinutes": 60,
				  "maxAdvanceDays": 30 }
				""".formatted(serviceId, poolId);
	}

	static String service(Object categoryId) {
		return """
				{ "name": "%s", "categoryId": "%s", "defaultDurationMinutes": 20 }
				""".formatted(ModuleFixtures.uniqueName("Service"), categoryId);
	}

	static String override(Object poolId, String from, String to, int capacity) {
		return """
				{ "poolId": "%s", "date": "%s", "window": { "start": "%s", "end": "%s" }, "capacity": %d,
				  "reason": "EXTRA_STAFF" }
				""".formatted(poolId, TOMORROW, from, to, capacity);
	}
}
