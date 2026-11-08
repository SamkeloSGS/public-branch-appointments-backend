package com.samdev.appointments;

import static com.samdev.appointments.identity.IdentityFixtures.token;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.samdev.appointments.identity.IdentityFixtures;

/**
 * Conventions every endpoint follows: field-level validation errors and paged lists.
 */
@IntegrationTest
class ApiConventionsTests {

	@Autowired MockMvc mvc;

	@Test
	@DisplayName("An invalid request body is a 400 naming each invalid field")
	void invalidBodyNamesTheFields() throws Exception {

		mvc.perform(post("/api/v1/customers/me").with(token(IdentityFixtures.newAccount(), 1))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "givenName": " ", "familyName": "" }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.detail").value("2 fields are invalid"))
				.andExpect(jsonPath("$.errors", hasSize(2)))
				.andExpect(jsonPath("$.errors[?(@.field == 'givenName')].message").value("must not be blank"))
				.andExpect(jsonPath("$.errors[?(@.field == 'familyName')].message").value("must not be blank"));
	}

	@Test
	@DisplayName("A body value of the wrong type is a 400 naming the field")
	void unreadableValueNamesTheField() throws Exception {

		mvc.perform(post("/api/v1/customers/me").with(token(IdentityFixtures.newAccount(), 1))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "givenName": { "first": "A" }, "familyName": "B" }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("givenName"));
	}

	@Test
	@DisplayName("Page and size are checked: size 1 to 100, page from 0, both numbers")
	void pageBoundsAreChecked() throws Exception {

		var caller = token(IdentityFixtures.newAccount(), 1);

		mvc.perform(get("/api/v1/appointments/mine").param("size", "101").with(caller))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("size"))
				.andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));
		mvc.perform(get("/api/v1/appointments/mine").param("page", "-1").with(caller))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("page"));
		mvc.perform(get("/api/v1/appointments/mine").param("page", "first").with(caller))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("page"))
				.andExpect(jsonPath("$.errors[0].message").value("must be a valid int"));

		mvc.perform(get("/api/v1/appointments/mine").param("page", "3").param("size", "5").with(caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(0)))
				.andExpect(jsonPath("$.page").value(3))
				.andExpect(jsonPath("$.size").value(5));
	}
}
