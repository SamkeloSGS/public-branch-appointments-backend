package com.samdev.appointments.branchmanagement.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.domain.entity.Service;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceCategory;

/**
 * The demo data loads through the module's commands, sets up the configured branch for the segments it serves, and
 * loading it again changes nothing.
 */
@IntegrationTest
@ActiveProfiles("local")
class DemoDataLoaderTests {

	@Autowired BranchQueries queries;
	@Autowired DemoDataLoader loader;

	@Test
	void setsUpTheBranch() {

		var hours = queries.openingHours().getWeeklyHours();

		assertThat(hours.on(DayOfWeek.MONDAY)).isNotEmpty();
		assertThat(hours.on(DayOfWeek.SATURDAY)).isNotEmpty();
		assertThat(hours.on(DayOfWeek.SUNDAY)).isEmpty();
		assertThat(queries.pools()).extracting(it -> it.getName())
				.contains("Consultants", "Business bankers", "Home Affairs desk");
		assertThat(queries.activeOfferings()).isNotEmpty();
		assertThat(queries.upcomingExceptions()).hasSize(4);
	}

	@Test
	void offeringsMatchSegmentsAndSmartId() {

		var services = queries.allServices().stream().collect(Collectors.toMap(Service::getId, Function.identity()));
		var homeAffairs = queries.categories(CustomerSegment.PERSONAL, false).stream()
				.filter(it -> it.getName().equals("Home Affairs"))
				.map(ServiceCategory::getId)
				.findFirst()
				.orElseThrow();
		var homeAffairsDesk = queries.pools().stream()
				.filter(it -> it.getName().equals("Home Affairs desk"))
				.findFirst()
				.orElseThrow();

		for (var offering : queries.offerings()) {

			var service = services.get(offering.getServiceId());

			assertThat(queries.branch().serves(service.getSegment())).as(service.getName()).isTrue();
			if (service.getCategoryId().equals(homeAffairs)) {
				assertThat(offering.getCapacityPoolId()).as(service.getName()).isEqualTo(homeAffairsDesk.getId());
			}
		}

		assertThat(queries.categories(null, false)).hasSize(10);
		assertThat(queries.activeServices(null, CustomerSegment.BUSINESS)).hasSize(17);
	}

	@Test
	void loadingAgainAddsNothing() throws Exception {

		var pools = queries.pools().size();
		var offerings = queries.offerings().size();
		var services = queries.allServices().size();
		var categories = queries.categories(null, true).size();

		loader.run(null);

		assertThat(queries.pools()).hasSize(pools);
		assertThat(queries.offerings()).hasSize(offerings);
		assertThat(queries.allServices()).hasSize(services);
		assertThat(queries.categories(null, true)).hasSize(categories);
	}
}
