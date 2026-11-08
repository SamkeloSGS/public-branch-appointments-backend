package com.samdev.appointments.branchmanagement.web.controller;

import static com.samdev.appointments.branchmanagement.web.controller.AdminApiTests.STANDARD_HOURS;
import static com.samdev.appointments.branchmanagement.web.controller.AdminApiTests.json;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.branchmanagement.ModuleFixtures;
import com.samdev.appointments.identity.IdentityFixtures;

/**
 * Who may call the Branch management admin endpoints.
 */
@IntegrationTest
class AuthorisationTests {

	@Autowired MockMvc mvc;
	@Autowired IdentityFixtures identity;
	Callers callers;

	@BeforeEach
	void callers() {
		callers = new Callers(identity);
	}

	@Test
	@DisplayName("BM-11 admin endpoints need a token")
	void adminEndpointsRequireAuthentication() throws Exception {

		mvc.perform(get("/api/v1/admin/branch")).andExpect(status().isUnauthorized());
		mvc.perform(json(post("/api/v1/admin/capacity-pools"), pool())).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("BM-11 branch staff may not manage the branch; the branch manager may")
	void managementNeedsTheBranchManager() throws Exception {

		mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), STANDARD_HOURS)
				.header(HttpHeaders.IF_MATCH, "*").with(callers.staff()))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/capacity-pools").with(callers.staff()))
				.andExpect(status().isForbidden());

		mvc.perform(json(put("/api/v1/admin/branch/weekly-hours"), STANDARD_HOURS)
				.header(HttpHeaders.IF_MATCH, "*").with(callers.manager()))
				.andExpect(status().isOk());
		mvc.perform(json(post("/api/v1/admin/capacity-pools"), pool()).with(callers.manager()))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("IA-2 role claims in the token have no effect; a signed-in person without a staff record is refused")
	void roleClaimsAreIgnored() throws Exception {

		var account = IdentityFixtures.newAccount();

		mvc.perform(get("/api/v1/admin/branch").with(jwt().jwt(it -> it.subject(account.subject().value())
				.claim("acr", "2")
				.claim("roles", List.of("BRANCH_MANAGER"))
				.claim("realm_access", Map.of("roles", List.of("BRANCH_MANAGER"))))))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/v1/admin/branch").with(callers.customer())).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("IA-4 an admin request with a level 1 token is refused with 401 insufficient_user_authentication")
	void adminNeedsSecondFactor() throws Exception {

		mvc.perform(get("/api/v1/admin/branch").with(callers.managerAtLevelOne()))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
						Matchers.containsString("error=\"insufficient_user_authentication\"")));

		mvc.perform(get("/api/v1/admin/branch").with(callers.manager())).andExpect(status().isOk());
	}

	@Test
	@DisplayName("IA-4 customer endpoints accept a level 1 token")
	void customerEndpointsAcceptLevelOne() throws Exception {

		mvc.perform(get("/api/v1/branch").with(IdentityFixtures.token(IdentityFixtures.newAccount(), 1)))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/me").with(IdentityFixtures.token(IdentityFixtures.newAccount(), 1)))
				.andExpect(status().isOk());
	}

	private static String pool() {
		return "{ \"name\": \"%s\", \"baseCapacity\": 1 }".formatted(ModuleFixtures.uniqueName("Consultants"));
	}
}
