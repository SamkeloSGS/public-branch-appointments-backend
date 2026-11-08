package com.samdev.appointments.branchmanagement.domain.entity;

import static com.samdev.appointments.branchmanagement.domain.entity.DomainFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceOffered;
import com.samdev.appointments.branchmanagement.ServiceOfferingChanged;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.ServiceOfferingWithdrawn;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.vo.BookingWindow;

class ServiceOfferingTests {

	BranchProfile branch = branch();
	CapacityPoolId poolId = CapacityPoolId.random();
	BookingWindow window = new BookingWindow(60, 30);

	@Test
	@DisplayName("BM-6 offering a service registers ServiceOffered, defaulting to the service's duration")
	void offersServiceWithDefaultDuration() {

		var service = service();
		var offering = ServiceOffering.offer(ServiceOfferingId.random(), branch, service, poolId, null,
				Duration.ofMinutes(15), window);

		assertThat(offering.getDuration()).isEqualTo(Duration.ofMinutes(30));

		var event = singleEvent(offering, ServiceOffered.class);
		assertThat(event.durationMinutes()).isEqualTo(30);
		assertThat(event.slotIntervalMinutes()).isEqualTo(15);
		assertThat(event.minLeadTimeMinutes()).isEqualTo(60);
		assertThat(event.maxAdvanceDays()).isEqualTo(30);
		assertThat(event.poolId()).isEqualTo(poolId);
	}

	@ParameterizedTest
	@CsvSource({ "45, 30", "30, 4", "240, 121", "0, 15" })
	@DisplayName("BM-6 duration must be a positive multiple of a 5 to 120 minute slot interval")
	void rejectsInvalidSchedule(int duration, int interval) {

		assertThatIllegalArgumentException().isThrownBy(() -> ServiceOffering.offer(ServiceOfferingId.random(),
				branch, service(), poolId, Duration.ofMinutes(duration), Duration.ofMinutes(interval), window));
	}

	@Test
	@DisplayName("BM-4/BM-6 a retired service cannot be offered")
	void rejectsRetiredService() {

		var service = service();
		service.retire();

		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> ServiceOffering.offer(
				ServiceOfferingId.random(), branch, service, poolId, null, Duration.ofMinutes(15), window));
	}

	@Test
	@DisplayName("BM-6 changing and withdrawing register their events")
	void changesAndWithdraws() {

		var offering = ServiceOffering.offer(ServiceOfferingId.random(), branch, service(), poolId, null,
				Duration.ofMinutes(15), window);
		clearEvents(offering);

		var otherPool = CapacityPoolId.random();
		offering.change(otherPool, Duration.ofMinutes(60), Duration.ofMinutes(20), new BookingWindow(0, 10));

		var changed = singleEvent(offering, ServiceOfferingChanged.class);
		assertThat(changed.poolId()).isEqualTo(otherPool);
		assertThat(changed.durationMinutes()).isEqualTo(60);
		clearEvents(offering);

		offering.withdraw();

		assertThat(offering.isActive()).isFalse();
		singleEvent(offering, ServiceOfferingWithdrawn.class);
		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(offering::withdraw);
		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(
				() -> offering.change(poolId, Duration.ofMinutes(30), Duration.ofMinutes(15), window));
	}
}
