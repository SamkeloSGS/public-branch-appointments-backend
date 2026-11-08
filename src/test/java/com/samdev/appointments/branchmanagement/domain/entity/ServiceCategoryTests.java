package com.samdev.appointments.branchmanagement.domain.entity;

import static com.samdev.appointments.branchmanagement.domain.entity.DomainFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryDefined;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceCategoryRetired;
import com.samdev.appointments.branchmanagement.ServiceCategoryUpdated;
import com.samdev.appointments.branchmanagement.ServiceDefined;
import com.samdev.appointments.branchmanagement.ServiceMovedToCategory;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.vo.BookingWindow;

class ServiceCategoryTests {

	// Categories

	@Test
	@DisplayName("BM-13 defining a category registers ServiceCategoryDefined with its segment")
	void definesCategory() {

		var category = category(CustomerSegment.BUSINESS);

		var event = singleEvent(category, ServiceCategoryDefined.class);
		assertThat(event.segment()).isEqualTo(CustomerSegment.BUSINESS);
		assertThat(event.name()).isEqualTo("Everyday banking");
		assertThat(category.isActive()).isTrue();

		assertThatIllegalArgumentException().isThrownBy(
				() -> ServiceCategory.define(ServiceCategoryId.random(), " ", CustomerSegment.PERSONAL, null));
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(
				() -> ServiceCategory.define(ServiceCategoryId.random(), "Loans", null, null));
	}

	@Test
	@DisplayName("BM-13 a category can be renamed and retired; a retired category accepts no changes")
	void updatesAndRetiresCategory() {

		var category = category(CustomerSegment.PERSONAL);
		clearEvents(category);

		category.update("Loans", "Home and vehicle loans");
		assertThat(singleEvent(category, ServiceCategoryUpdated.class).name()).isEqualTo("Loans");
		clearEvents(category);

		category.retire();
		singleEvent(category, ServiceCategoryRetired.class);

		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(category::retire);
		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> category.update("Other", null));
	}

	// Services in categories

	@Test
	@DisplayName("BM-13 a service belongs to one active category and takes its segment")
	void serviceTakesCategoryAndSegment() {

		var category = category(CustomerSegment.BUSINESS);
		var service = service(category);

		assertThat(service.getCategoryId()).isEqualTo(category.getId());
		assertThat(service.getSegment()).isEqualTo(CustomerSegment.BUSINESS);

		var event = singleEvent(service, ServiceDefined.class);
		assertThat(event.categoryId()).isEqualTo(category.getId());
		assertThat(event.segment()).isEqualTo(CustomerSegment.BUSINESS);

		category.retire();
		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> service(category));
	}

	@Test
	@DisplayName("BM-13 a service moves only to another active category of the same segment")
	void movesWithinSegment() {

		var service = service(category(CustomerSegment.PERSONAL));
		var other = ServiceCategory.define(ServiceCategoryId.random(), "Savings", CustomerSegment.PERSONAL, null);
		clearEvents(service);

		service.moveTo(other);

		assertThat(service.getCategoryId()).isEqualTo(other.getId());
		assertThat(singleEvent(service, ServiceMovedToCategory.class).categoryId()).isEqualTo(other.getId());

		assertThatExceptionOfType(BusinessRuleViolation.class).as("same category")
				.isThrownBy(() -> service.moveTo(other));
		assertThatExceptionOfType(BusinessRuleViolation.class).as("other segment")
				.isThrownBy(() -> service.moveTo(category(CustomerSegment.BUSINESS)));

		var retired = ServiceCategory.define(ServiceCategoryId.random(), "Old", CustomerSegment.PERSONAL, null);
		retired.retire();
		assertThatExceptionOfType(BusinessRuleViolation.class).as("retired category")
				.isThrownBy(() -> service.moveTo(retired));
	}

	// Branch segments

	@Test
	@DisplayName("BM-13 the branch serves at least one customer segment")
	void branchServesAtLeastOneSegment() {

		assertThat(branch().segments()).containsExactlyInAnyOrder(CustomerSegment.PERSONAL, CustomerSegment.BUSINESS);
		assertThatIllegalArgumentException().isThrownBy(() -> branch(Set.of()));
	}

	@Test
	@DisplayName("BM-13 the branch can only offer services of a segment it serves")
	void offeringNeedsServedSegment() {

		var branch = branch(Set.of(CustomerSegment.PERSONAL));

		var window = new BookingWindow(0, 30);
		var business = service(category(CustomerSegment.BUSINESS));
		var personal = service(category(CustomerSegment.PERSONAL));

		assertThatExceptionOfType(BusinessRuleViolation.class)
				.isThrownBy(() -> ServiceOffering.offer(ServiceOfferingId.random(), branch, business,
						CapacityPoolId.random(), null, Duration.ofMinutes(15), window))
				.withMessageContaining("BUSINESS");

		assertThat(ServiceOffering.offer(ServiceOfferingId.random(), branch, personal, CapacityPoolId.random(), null,
				Duration.ofMinutes(15), window)).isNotNull();
	}
}
