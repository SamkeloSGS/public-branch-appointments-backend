package com.samdev.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * The OpenAPI description and Swagger UI are public and describe both API groups.
 */
@IntegrationTest
class ApiDocumentationTests {

	@Autowired MockMvc mvc;

	@Test
	void describesAdminApiWithKeycloakSignIn() throws Exception {

		mvc.perform(get("/v3/api-docs/admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Branch Appointments API"))
				.andExpect(jsonPath("$.paths", hasKey("/api/v1/admin/branch/weekly-hours")))
				.andExpect(jsonPath("$.paths", not(hasKey("/api/v1/branch"))))
				.andExpect(jsonPath("$.paths", hasKey("/api/v1/admin/staff")))
				.andExpect(jsonPath("$.components.securitySchemes.keycloak.type").value("oauth2"))
				.andExpect(jsonPath("$.components.securitySchemes.keycloak.flows.authorizationCode.authorizationUrl")
						.value(containsString("/realms/appointments/protocol/openid-connect/auth")))
				.andExpect(jsonPath("$.paths['/api/v1/admin/capacity-pools'].post.security[0]", hasKey("keycloak")));
	}

	@Test
	void describesPublicApiWithoutSecurity() throws Exception {

		mvc.perform(get("/v3/api-docs/public"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths", hasKey("/api/v1/branch")))
				.andExpect(jsonPath("$.paths", not(hasKey("/api/v1/admin/branch"))))
				.andExpect(jsonPath("$.paths['/api/v1/branch'].get.security").doesNotExist());
	}

	@Test
	@DisplayName("Every endpoint is under /api/v1; an unversioned /api path is refused")
	void unversionedPathsAreRefused() throws Exception {
		mvc.perform(get("/api/v1/branch")).andExpect(status().isOk());
		mvc.perform(get("/api/branch")).andExpect(status().is4xxClientError());

		for (var group : List.of("public", "admin")) {
			var body = mvc.perform(get("/v3/api-docs/" + group)).andReturn().getResponse().getContentAsString();
			Map<String, Object> paths = JsonPath.read(body, "$.paths");
			assertThat(paths.keySet()).as(group).isNotEmpty().allMatch(it -> it.startsWith("/api/v1/"));
		}
	}

	@Test
	void servesSwaggerUi() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}
}
