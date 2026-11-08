package com.samdev.appointments.identity.web.controller;

import static com.samdev.appointments.identity.IdentityFixtures.token;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.identity.IdentityFixtures;
import com.samdev.appointments.identity.StaffRole;

/**
 * {@code GET /api/v1/me} and the staff endpoints: status codes, rule codes, ETag and If-Match.
 */
@IntegrationTest
class IdentityApiTests {

	@Autowired MockMvc mvc;
	@Autowired IdentityFixtures identity;

	@Test
	@DisplayName("IA-17/IA-2 /me for a customer: role CUSTOMER, no name")
	void meAsCustomer() throws Exception {

		mvc.perform(get("/api/v1/me").with(token(IdentityFixtures.newAccount(), 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.displayName").doesNotExist())
				.andExpect(jsonPath("$.secondFactor").value(false));

		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("IA-17/IA-2 /me for active staff: stored role and display name")
	void meAsStaff() throws Exception {

		var staff = identity.activeStaff(StaffRole.BRANCH_MANAGER);

		mvc.perform(get("/api/v1/me").with(token(staff, 2)))
				.andExpect(jsonPath("$.role").value("BRANCH_MANAGER"))
				.andExpect(jsonPath("$.displayName").value(containsString("Staff")))
				.andExpect(jsonPath("$.secondFactor").value(true));
	}

	@Test
	@DisplayName("IA-5 invite returns 201 with the sign-up address; rule violations return 409 with a code")
	void invites() throws Exception {

		var admin = token(identity.activeStaff(StaffRole.BRANCH_MANAGER), 2);
		var email = "new." + UUID.randomUUID().toString().substring(0, 8) + "@capitec.test";

		mvc.perform(json(post("/api/v1/admin/staff"), invite(email, "BRANCH_STAFF")).with(admin))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
				.andExpect(jsonPath("$.staffMember.status").value("INVITED"))
				.andExpect(jsonPath("$.staffMember.invitationExpiresAt").exists())
				.andExpect(jsonPath("$.signUpUrl").value(containsString("/sign-up")));

		mvc.perform(json(post("/api/v1/admin/staff"), invite(email, "BRANCH_MANAGER")).with(admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));

		mvc.perform(json(post("/api/v1/admin/staff"), invite("y" + email, "HEAD_OFFICE_ADMIN")).with(admin))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("IA-9 a suspended staff member's next admin request is refused with 403; reinstating restores it")
	void suspensionAppliesOnNextRequest() throws Exception {

		var admin = token(identity.activeStaff(StaffRole.BRANCH_MANAGER), 2);
		var member = identity.activeStaff(StaffRole.BRANCH_MANAGER);
		var path = "/api/v1/admin/staff/" + member.id();

		mvc.perform(get("/api/v1/admin/staff").with(token(member, 2))).andExpect(status().isOk());

		mvc.perform(post(path + "/suspend").header(HttpHeaders.IF_MATCH, "\"0\"").with(admin))
				.andExpect(status().isPreconditionFailed());

		mvc.perform(post(path + "/suspend").header(HttpHeaders.IF_MATCH, "\"1\"").with(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUSPENDED"));

		mvc.perform(get("/api/v1/admin/staff").with(token(member, 2))).andExpect(status().isForbidden());

		mvc.perform(post(path + "/reinstate").header(HttpHeaders.IF_MATCH, "\"2\"").with(admin))
				.andExpect(status().isOk());

		mvc.perform(get("/api/v1/admin/staff").with(token(member, 2))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("IA-7/IA-10/IA-11 role, removal and the self-change rule over HTTP")
	void manageStaff() throws Exception {

		var adminAccount = identity.activeStaff(StaffRole.BRANCH_MANAGER);
		var admin = token(adminAccount, 2);
		var member = identity.activeStaff(StaffRole.BRANCH_STAFF);
		var path = "/api/v1/admin/staff/" + member.id();

		mvc.perform(json(put(path + "/role"), "{ \"role\": \"BRANCH_MANAGER\" }")
				.header(HttpHeaders.IF_MATCH, "\"1\"").with(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("BRANCH_MANAGER"))
				.andExpect(header().string(HttpHeaders.ETAG, "\"2\""));

		mvc.perform(get("/api/v1/me").with(token(member, 2))).andExpect(jsonPath("$.role").value("BRANCH_MANAGER"));

		mvc.perform(post("/api/v1/admin/staff/" + adminAccount.id() + "/suspend").header(HttpHeaders.IF_MATCH, "*")
				.with(admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SELF_CHANGE_NOT_ALLOWED"));

		mvc.perform(delete(path).header(HttpHeaders.IF_MATCH, "\"2\"").with(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REMOVED"))
				.andExpect(jsonPath("$.email").doesNotExist());

		mvc.perform(get(path + "/changes").with(admin))
				.andExpect(jsonPath("$[*].action", hasItem("REMOVED")))
				.andExpect(jsonPath("$[*].detail", not(hasItem(containsString("@")))));

		mvc.perform(get("/api/v1/admin/staff").param("status", "REMOVED").with(admin))
				.andExpect(jsonPath("$[*].id", hasItem(member.id().toString())));
	}

	@Test
	@DisplayName("IA-4/IA-11 staff endpoints need the branch manager at level 2")
	void staffEndpointsNeedTheBranchManager() throws Exception {

		var staff = identity.activeStaff(StaffRole.BRANCH_STAFF);

		mvc.perform(get("/api/v1/admin/staff").with(token(staff, 2))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/staff").with(token(identity.activeStaff(StaffRole.BRANCH_MANAGER), 1)))
				.andExpect(status().isUnauthorized());
	}

	static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	static String invite(String email, String role) {
		return """
				{ "email": "%s", "displayName": "New staff", "role": "%s" }
				""".formatted(email, role);
	}
}
