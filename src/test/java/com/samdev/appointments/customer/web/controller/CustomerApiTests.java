package com.samdev.appointments.customer.web.controller;

import static com.samdev.appointments.identity.IdentityFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.identity.IdentityFixtures;
import com.samdev.appointments.identity.StaffRole;

/**
 * Self-service and admin customer endpoints: status codes, rule codes, ETag and If-Match, roles and audit rows.
 */
@IntegrationTest
class CustomerApiTests {

	@Autowired MockMvc mvc;
	@Autowired IdentityFixtures identity;
	@Autowired JdbcTemplate jdbc;

	// Self-service

	@Test
	@DisplayName("CU-11/CU-1 without a profile GET, PUT and DELETE return 404; POST creates one at sign-in level 1")
	void selfRegistration() throws Exception {

		var account = IdentityFixtures.newAccount();
		var caller = token(account, 1);

		mvc.perform(get("/api/v1/customers/me").with(caller)).andExpect(status().isNotFound());
		mvc.perform(json(put("/api/v1/customers/me"), details("A", "B", email(), null)).header(HttpHeaders.IF_MATCH, "*")
				.with(caller)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/customers/me").header(HttpHeaders.IF_MATCH, "*").with(caller))
				.andExpect(status().isNotFound());

		mvc.perform(json(post("/api/v1/customers/me"), customer("Thandi", "Nkosi", null, "082 555 0142", null))
						.with(caller))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
				.andExpect(jsonPath("$.email").value(account.email()))
				.andExpect(jsonPath("$.phone").value("+27825550142"))
				.andExpect(jsonPath("$.emailEnabled").value(true))
				.andExpect(jsonPath("$.smsEnabled").value(true))
				.andExpect(jsonPath("$.language").value("en"))
				.andExpect(jsonPath("$.linked").value(true));

		mvc.perform(json(post("/api/v1/customers/me"), customer("Thandi", "Nkosi", null, null, null)).with(caller))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_EXISTS"));

		mvc.perform(get("/api/v1/customers/me").with(caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.givenName").value("Thandi"));

		mvc.perform(get("/api/v1/customers/me")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("CU-1 invalid input returns 400 and creates nothing")
	void invalidRegistration() throws Exception {

		var account = IdentityFixtures.newAccount();
		var unverified = token(account.subject(), account.email(), false, 1);

		mvc.perform(json(post("/api/v1/customers/me"), customer("A", "B", null, null, null)).with(unverified))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/v1/customers/me"), customer("", "B", email(), null, null)).with(unverified))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/v1/customers/me"), customer("A", "B", "not-an-email", null, null)).with(unverified))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/v1/customers/me"), customer("A", "B", null, "12345", null)).with(unverified))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/v1/customers/me"), customer("A", "B", email(), null, "fr")).with(unverified))
				.andExpect(status().isBadRequest());

		mvc.perform(get("/api/v1/customers/me").with(unverified)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("CU-2/CU-7 an email of another customer gets a 409 that does not say why")
	void doesNotLeakEmailOwnership() throws Exception {

		var taken = email();
		staffCreates(taken);

		mvc.perform(json(post("/api/v1/customers/me"), customer("A", "B", taken, null, null))
						.with(token(IdentityFixtures.newAccount(), 1)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONTACT_DETAILS_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail", not(containsString(taken))))
				.andExpect(jsonPath("$.detail", not(containsString("exist"))))
				.andExpect(jsonPath("$.existingCustomerId").doesNotExist());
	}

	@Test
	@DisplayName("CU-2 signing up with the verified email of a staff-created customer links that customer")
	void linksByVerifiedEmail() throws Exception {

		var email = email();
		var id = staffCreates(email);
		var account = new IdentityFixtures.Account(IdentityFixtures.newAccount().subject(), email);

		mvc.perform(json(post("/api/v1/customers/me"), customer("Other", "Name", null, null, null)).with(token(account, 1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.givenName").value("Walk"))
				.andExpect(jsonPath("$.linked").value(true));
	}

	@Test
	@DisplayName("CU-4/CU-5/CU-12 updates need If-Match; a stale version gets 412; removing email switches to SMS")
	void selfUpdates() throws Exception {

		var account = IdentityFixtures.newAccount();
		var caller = token(account, 1);
		register(caller, "082 555 0143");

		mvc.perform(json(put("/api/v1/customers/me"), details("Thandi", "Nkosi", null, "082 555 0143")).with(caller))
				.andExpect(status().isPreconditionRequired());

		mvc.perform(json(put("/api/v1/customers/me/contact-preferences"), "{\"emailEnabled\":true,\"smsEnabled\":false}")
						.header(HttpHeaders.IF_MATCH, "\"0\"").with(caller))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
				.andExpect(jsonPath("$.smsEnabled").value(false));

		mvc.perform(json(put("/api/v1/customers/me"), details("Thandi", "Nkosi", null, "082 555 0143"))
						.header(HttpHeaders.IF_MATCH, "\"1\"").with(caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.emailEnabled").value(false))
				.andExpect(jsonPath("$.smsEnabled").value(true));

		mvc.perform(json(put("/api/v1/customers/me"), details("Thandi", "Nkosi", account.email(), "082 555 0143"))
						.header(HttpHeaders.IF_MATCH, "\"1\"").with(caller))
				.andExpect(status().isPreconditionFailed());

		mvc.perform(json(put("/api/v1/customers/me"), details("Thandi", "Nkosi", null, null))
						.header(HttpHeaders.IF_MATCH, "*").with(caller))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONTACT_REQUIRED"));

		mvc.perform(json(put("/api/v1/customers/me/contact-preferences"), "{\"emailEnabled\":true,\"smsEnabled\":true}")
						.header(HttpHeaders.IF_MATCH, "*").with(caller))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CHANNEL_WITHOUT_CONTACT"));

		mvc.perform(json(put("/api/v1/customers/me/contact-preferences"),
						"{\"emailEnabled\":false,\"smsEnabled\":true,\"language\":\"de\"}")
						.header(HttpHeaders.IF_MATCH, "*").with(caller))
				.andExpect(status().isBadRequest());

		assertThat(auditRowsFor(caller)).as("self-service writes no audit rows").isZero();
	}

	@Test
	@DisplayName("CU-6/CU-7 a deactivated customer can view and erase, not change; after erasure the profile is gone")
	void deactivatedCustomer() throws Exception {

		var caller = token(IdentityFixtures.newAccount(), 1);
		var id = register(caller, null);
		var manager = token(identity.activeStaff(StaffRole.BRANCH_MANAGER), 2);

		mvc.perform(post("/api/v1/admin/customers/{id}/deactivate", id).header(HttpHeaders.IF_MATCH, "*").with(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DEACTIVATED"));

		mvc.perform(get("/api/v1/customers/me").with(caller)).andExpect(jsonPath("$.status").value("DEACTIVATED"));
		mvc.perform(json(put("/api/v1/customers/me"), details("A", "B", email(), null)).header(HttpHeaders.IF_MATCH, "*")
				.with(caller)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
		mvc.perform(json(put("/api/v1/customers/me/contact-preferences"), "{\"emailEnabled\":true,\"smsEnabled\":false}")
				.header(HttpHeaders.IF_MATCH, "*").with(caller)).andExpect(status().isConflict());

		mvc.perform(delete("/api/v1/customers/me").with(caller)).andExpect(status().isPreconditionRequired());
		mvc.perform(delete("/api/v1/customers/me").header(HttpHeaders.IF_MATCH, "*").with(caller))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/customers/me").with(caller)).andExpect(status().isNotFound());

		mvc.perform(get("/api/v1/admin/customers/{id}", id).with(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ERASED"))
				.andExpect(jsonPath("$.givenName").doesNotExist())
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	// Admin

	@Test
	@DisplayName("CU-11/CU-3/CU-8 branch staff search, view, create and correct, each audited; status changes need the branch manager")
	void branchStaff() throws Exception {

		var staffAccount = identity.activeStaff(StaffRole.BRANCH_STAFF);
		var staff = token(staffAccount, 2);
		var email = email();

		var created = mvc.perform(json(post("/api/v1/admin/customers"), customer("Walk", "In", email, "082 555 0144", "zu"))
						.with(staff))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.linked").value(false))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");

		mvc.perform(json(post("/api/v1/admin/customers"), customer("Walk", "In", email, null, null)).with(staff))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_IN_USE"))
				.andExpect(jsonPath("$.existingCustomerId").value(id));

		mvc.perform(get("/api/v1/admin/customers").param("query", email).with(staff))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(id))
				.andExpect(jsonPath("$.totalItems").value(1));
		mvc.perform(get("/api/v1/admin/customers").param("query", "ab").with(staff)).andExpect(status().isBadRequest());

		mvc.perform(get("/api/v1/admin/customers/{id}", id).with(staff))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"0\""));

		mvc.perform(json(put("/api/v1/admin/customers/{id}", id), details("Walk", "Inn", email, null))
						.header(HttpHeaders.IF_MATCH, "\"0\"").with(staff))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.familyName").value("Inn"));

		for (var builder : new MockHttpServletRequestBuilder[] {
				post("/api/v1/admin/customers/{id}/deactivate", id), post("/api/v1/admin/customers/{id}/reactivate", id),
				delete("/api/v1/admin/customers/{id}", id) }) {
			mvc.perform(builder.header(HttpHeaders.IF_MATCH, "*").with(staff)).andExpect(status().isForbidden());
		}

		assertThat(jdbc.queryForList("SELECT action FROM customer.access_audit WHERE staff_subject = ?", String.class,
				staffAccount.subject().value())).containsExactlyInAnyOrder("CREATE", "SEARCH", "VIEW", "UPDATE");
	}

	@Test
	@DisplayName("CU-11/CU-6/CU-7 the branch manager deactivates, reactivates and erases; invalid transitions are 409")
	void branchManager() throws Exception {

		var admin = token(identity.activeStaff(StaffRole.BRANCH_MANAGER), 2);
		var id = staffCreates(email());

		mvc.perform(post("/api/v1/admin/customers/{id}/reactivate", id).header(HttpHeaders.IF_MATCH, "*").with(admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		mvc.perform(post("/api/v1/admin/customers/{id}/deactivate", id).with(admin))
				.andExpect(status().isPreconditionRequired());
		mvc.perform(post("/api/v1/admin/customers/{id}/deactivate", id).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
				.andExpect(status().isOk());
		mvc.perform(post("/api/v1/admin/customers/{id}/reactivate", id).header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
				.andExpect(status().isPreconditionFailed());
		mvc.perform(post("/api/v1/admin/customers/{id}/reactivate", id).header(HttpHeaders.IF_MATCH, "\"1\"").with(admin))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		mvc.perform(delete("/api/v1/admin/customers/{id}", id).header(HttpHeaders.IF_MATCH, "*").with(admin))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/admin/customers/{id}", id).header(HttpHeaders.IF_MATCH, "*").with(admin))
				.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/admin/customers/{id}", UUID.randomUUID()).with(admin)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("CU-11 customers cannot reach the admin endpoints")
	void customersAreRefused() throws Exception {

		var customer = token(IdentityFixtures.newAccount(), 2);

		mvc.perform(get("/api/v1/admin/customers").param("query", "abc").with(customer)).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/v1/admin/customers"), customer("A", "B", email(), null, null)).with(customer))
				.andExpect(status().isForbidden());
	}

	private String staffCreates(String email) throws Exception {

		var staff = token(identity.activeStaff(StaffRole.BRANCH_STAFF), 2);
		var body = mvc.perform(json(post("/api/v1/admin/customers"), customer("Walk", "In", email, null, null)).with(staff))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		return JsonPath.read(body, "$.id");
	}

	private String register(RequestPostProcessor caller, String phone) throws Exception {

		var body = mvc.perform(json(post("/api/v1/customers/me"), customer("Thandi", "Nkosi", null, phone, null))
						.with(caller))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		return JsonPath.read(body, "$.id");
	}

	private long auditRowsFor(RequestPostProcessor caller) throws Exception {

		var body = mvc.perform(get("/api/v1/customers/me").with(caller)).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		return jdbc.queryForObject("SELECT count(*) FROM customer.access_audit WHERE customer_id = ?::uuid", Long.class,
				id);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String customer(String givenName, String familyName, String email, String phone, String language) {
		return "{\"givenName\":\"%s\",\"familyName\":\"%s\",\"email\":%s,\"phone\":%s,\"language\":%s}".formatted(
				givenName, familyName, quoted(email), quoted(phone), quoted(language));
	}

	private static String details(String givenName, String familyName, String email, String phone) {
		return "{\"givenName\":\"%s\",\"familyName\":\"%s\",\"email\":%s,\"phone\":%s}".formatted(givenName, familyName,
				quoted(email), quoted(phone));
	}

	private static String quoted(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private static String email() {
		return "person-" + UUID.randomUUID().toString().substring(0, 8) + "@customers.test";
	}
}
