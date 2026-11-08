package com.samdev.appointments;

import static com.samdev.appointments.identity.IdentityFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.samdev.appointments.identity.IdentityFixtures;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

/**
 * The access test from the system design: every admin endpoint in every module, found from the request mappings,
 * tried with a level 1 token and a customer, and every manager-only endpoint with branch staff. No endpoint may let
 * any of them through, and refusal must come before the request body is looked at.
 */
@IntegrationTest
class AdminAccessMatrixTests {

	@Autowired MockMvc mvc;
	@Autowired IdentityFixtures identity;
	@Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

	/**
	 * @param managerOnly whether the endpoint needs the branch manager, so branch staff must be refused too.
	 *     Customers, appointments and notifications are open to all branch staff.
	 */
	record Endpoint(HttpMethod method, String path, boolean managerOnly) {}

	@Test
	@DisplayName("IA-3/IA-4/BM-11/CU-11/BK-16 every admin endpoint refuses level 1 tokens and customers; management refuses branch staff")
	void everyAdminEndpointIsProtected() throws Exception {

		var endpoints = adminEndpoints();
		assertThat(endpoints).hasSizeGreaterThan(30);
		assertThat(endpoints).filteredOn(it -> !it.managerOnly())
				.as("only customers, appointments and notifications are open to branch staff")
				.allSatisfy(it -> assertThat(it.path())
						.matches("/api/v1/admin/(customers|appointments|notifications).*"));

		var levelOne = token(identity.activeStaff(StaffRole.BRANCH_MANAGER), 1);
		var customer = token(IdentityFixtures.newAccount(), 2);
		var staff = token(identity.activeStaff(StaffRole.BRANCH_STAFF), 2);

		var failures = new ArrayList<String>();

		for (var endpoint : endpoints) {
			expect(endpoint, levelOne, 401, failures);
			expect(endpoint, customer, 403, failures);
			if (endpoint.managerOnly()) {
				expect(endpoint, staff, 403, failures);
			}
		}

		assertThat(failures).isEmpty();
	}

	private void expect(Endpoint endpoint, RequestPostProcessor caller, int status, List<String> failures)
			throws Exception {

		var actual = mvc.perform(request(endpoint.method(), endpoint.path())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.header(HttpHeaders.IF_MATCH, "*")
						.with(caller))
				.andReturn().getResponse().getStatus();

		if (actual != status) {
			failures.add(endpoint + " expected " + status + " but was " + actual);
		}
	}

	private List<Endpoint> adminEndpoints() {
		return mappings.getHandlerMethods().entrySet().stream()
				.flatMap(entry -> entry.getKey().getPatternValues().stream()
						.filter(it -> it.startsWith("/api/v1/admin"))
						.flatMap(pattern -> entry.getKey().getMethodsCondition().getMethods().stream()
								.map(method -> new Endpoint(HttpMethod.valueOf(method.name()),
										pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString()),
										isManagerOnly(entry.getValue())))))
				.distinct()
				.toList();
	}

	private static boolean isManagerOnly(HandlerMethod handler) {

		var role = handler.hasMethodAnnotation(RequiresRole.class)
				? handler.getMethodAnnotation(RequiresRole.class)
				: AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiresRole.class);

		return role != null && role.value() == StaffRole.BRANCH_MANAGER;
	}
}
