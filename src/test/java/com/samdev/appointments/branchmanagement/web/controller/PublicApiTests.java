package com.samdev.appointments.branchmanagement.web.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ModuleFixtures;
import com.samdev.appointments.branchmanagement.application.command.RetireService;
import com.samdev.appointments.branchmanagement.application.command.RetireServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.WithdrawServiceOffering;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCatalogCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCategoryCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceOfferingCommandHandler;

/**
 * The customer-facing endpoints, without a token.
 */
@IntegrationTest
class PublicApiTests {

	@Autowired MockMvc mvc;
	@Autowired ModuleFixtures fixtures;
	@Autowired ServiceCatalogCommandHandler services;
	@Autowired ServiceCategoryCommandHandler categories;
	@Autowired ServiceOfferingCommandHandler offerings;
	@Autowired CacheManager caches;

	@Test
	@DisplayName("BM-1/BM-9 GET /branch shows the configured branch with its hours and active offerings only")
	void showsTheBranch() throws Exception {

		var offered = fixtures.offeredService();
		var withdrawn = fixtures.offeredService();
		offerings.handle(new WithdrawServiceOffering(withdrawn.offeringId(), null));

		mvc.perform(get("/api/v1/branch"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("MAIN"))
				.andExpect(jsonPath("$.name").value("Main Branch"))
				.andExpect(jsonPath("$.timeZone").value("Africa/Johannesburg"))
				.andExpect(jsonPath("$.weeklyHours.TUESDAY", hasSize(1)))
				.andExpect(jsonPath("$.services[*].serviceId", hasItem(offered.serviceId().toString())))
				.andExpect(jsonPath("$.services[?(@.serviceId == '%s')].prerequisites".formatted(offered.serviceId()))
						.exists())
				.andExpect(jsonPath("$.services[*].serviceId", not(hasItem(withdrawn.serviceId().toString()))));
	}

	@Test
	@DisplayName("BM-4 GET /services lists only active services")
	void listsActiveServices() throws Exception {

		var active = fixtures.service();
		var retired = fixtures.service();
		services.handle(new RetireService(retired, null));

		mvc.perform(get("/api/v1/services"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(active.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(retired.toString()))));
	}

	@Test
	@DisplayName("BM-13 services filter by category and segment; categories list their active services")
	void servicesByCategory() throws Exception {

		var personal = fixtures.category(CustomerSegment.PERSONAL);
		var business = fixtures.category(CustomerSegment.BUSINESS);
		var retired = fixtures.category(CustomerSegment.PERSONAL);
		var personalService = fixtures.service(personal);
		var businessService = fixtures.service(business);
		categories.handle(new RetireServiceCategory(retired, null));

		mvc.perform(get("/api/v1/services").param("categoryId", personal.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", contains(personalService.toString())));

		mvc.perform(get("/api/v1/services").param("segment", "BUSINESS"))
				.andExpect(jsonPath("$[*].id", hasItem(businessService.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(personalService.toString()))))
				.andExpect(jsonPath("$[*].segment", everyItem(is("BUSINESS"))));

		mvc.perform(get("/api/v1/service-categories").param("segment", "PERSONAL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].segment", everyItem(is("PERSONAL"))))
				.andExpect(jsonPath("$[?(@.id == '%s')].services[*].id".formatted(personal),
						contains(personalService.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(business.toString()))))
				.andExpect(jsonPath("$[*].id", not(hasItem(retired.toString()))));

		mvc.perform(get("/api/v1/services").param("segment", "RETAIL"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("BM-13 the branch shows the segments it serves and offers services of both")
	void showsSegments() throws Exception {

		var poolId = fixtures.pool();
		var personal = fixtures.service(CustomerSegment.PERSONAL);
		var business = fixtures.service(CustomerSegment.BUSINESS);
		fixtures.offering(personal, poolId);
		fixtures.offering(business, poolId);

		mvc.perform(get("/api/v1/branch"))
				.andExpect(jsonPath("$.segments", containsInAnyOrder("PERSONAL", "BUSINESS")))
				.andExpect(jsonPath("$.services[*].serviceId", hasItems(personal.toString(), business.toString())))
				.andExpect(jsonPath("$.services[*].segment", hasItems("PERSONAL", "BUSINESS")));
	}

	@Test
	@DisplayName("BM-1/BM-4 public reads are cached in Redis and emptied when the catalog changes")
	void cachesPublicReads() throws Exception {

		mvc.perform(get("/api/v1/branch")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/services")).andExpect(status().isOk());
		assertThat(caches.getCache("public-branch").get("branch")).as("cached after the first read").isNotNull();
		assertThat(caches.getCache("public-services").get("null:null")).isNotNull();

		var offered = fixtures.offeredService();

		assertThat(caches.getCache("public-branch").get("branch")).as("emptied by the change").isNull();
		mvc.perform(get("/api/v1/branch"))
				.andExpect(jsonPath("$.services[*].serviceId", hasItem(offered.serviceId().toString())));
		mvc.perform(get("/api/v1/services"))
				.andExpect(jsonPath("$[*].id", hasItem(offered.serviceId().toString())));
	}
}
