package com.samdev.appointments.branchmanagement.domain.entity;

import static com.samdev.appointments.branchmanagement.domain.entity.DomainFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.branchmanagement.CapacityPoolBaseCapacityChanged;
import com.samdev.appointments.branchmanagement.CapacityPoolDefined;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceDefined;
import com.samdev.appointments.branchmanagement.ServiceDetailsUpdated;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceRetired;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.vo.Capacity;

class CatalogAndPoolTests {

	@Test
	@DisplayName("BM-4 defining a service registers ServiceDefined")
	void definesService() {

		var service = service();

		assertThat(service.isActive()).isTrue();
		assertThat(singleEvent(service, ServiceDefined.class).defaultDurationMinutes()).isEqualTo(30);
	}

	@Test
	@DisplayName("BM-4 prerequisites keep their order; blank ones are dropped, at most 10 of up to 200 characters")
	void validatesPrerequisites() {

		var category = category(CustomerSegment.PERSONAL);
		var service = Service.define(ServiceId.random(), "Open an account", category, null,
				List.of(" Your ID document ", " ", "Proof of address"), Duration.ofMinutes(30));

		assertThat(service.getPrerequisites()).containsExactly("Your ID document", "Proof of address");
		assertThatIllegalArgumentException().isThrownBy(() -> Service.define(ServiceId.random(), "X", category, null,
				List.of("x".repeat(201)), Duration.ofMinutes(30)));
		assertThatIllegalArgumentException().isThrownBy(() -> Service.define(ServiceId.random(), "X", category, null,
				Collections.nCopies(11, "Item"), Duration.ofMinutes(30)));
	}

	@Test
	@DisplayName("BM-4 updating the description and prerequisites registers ServiceDetailsUpdated")
	void updatesDetails() {

		var service = service();
		clearEvents(service);

		service.updateDetails("Bring your card", List.of("Your card"));

		assertThat(service.getDescription()).isEqualTo("Bring your card");
		assertThat(service.getPrerequisites()).containsExactly("Your card");
		assertThat(singleEvent(service, ServiceDetailsUpdated.class).serviceId()).isEqualTo(service.getId());

		service.updateDetails(" ", List.of());
		assertThat(service.getDescription()).isNull();
		assertThat(service.getPrerequisites()).isEmpty();
	}

	@Test
	@DisplayName("BM-4 default duration is positive and in whole minutes")
	void rejectsInvalidDurations() {

		var category = category(CustomerSegment.PERSONAL);

		assertThatIllegalArgumentException()
				.isThrownBy(() -> Service.define(ServiceId.random(), "X", category, null, List.of(), Duration.ZERO));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> Service.define(ServiceId.random(), "X", category, null, List.of(), Duration.ofSeconds(90)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> Service.define(ServiceId.random(), "x".repeat(81), category, null, List.of(), Duration.ofMinutes(5)));
	}

	@Test
	@DisplayName("BM-4 retiring a service registers ServiceRetired once")
	void retiresService() {

		var service = service();
		clearEvents(service);

		service.retire();

		assertThat(service.isActive()).isFalse();
		singleEvent(service, ServiceRetired.class);
		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(service::retire);
	}

	@Test
	@DisplayName("BM-5 defining a pool registers CapacityPoolDefined")
	void definesPool() {

		var pool = pool();

		var event = singleEvent(pool, CapacityPoolDefined.class);
		assertThat(event.name()).isEqualTo("Consultants");
		assertThat(event.baseCapacity()).isEqualTo(3);
	}

	@Test
	@DisplayName("BM-5 changing base capacity registers CapacityPoolBaseCapacityChanged")
	void changesBaseCapacity() {

		var pool = pool();
		clearEvents(pool);

		pool.changeBaseCapacity(new Capacity(5));

		assertThat(pool.getBaseCapacity()).isEqualTo(new Capacity(5));
		assertThat(singleEvent(pool, CapacityPoolBaseCapacityChanged.class).baseCapacity()).isEqualTo(5);
	}
}
