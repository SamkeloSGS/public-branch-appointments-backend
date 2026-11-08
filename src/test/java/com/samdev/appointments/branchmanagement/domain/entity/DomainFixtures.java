package com.samdev.appointments.branchmanagement.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.Address;
import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.GeoLocation;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.domain.vo.Capacity;
import com.samdev.appointments.branchmanagement.domain.vo.WeeklyHours;

/**
 * Builders for domain unit tests. Lives in the domain package to read the aggregates' registered events.
 */
final class DomainFixtures {

	static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

	private DomainFixtures() {}

	/**
	 * A branch serving both personal and business customers.
	 */
	static BranchProfile branch() {
		return branch(EnumSet.allOf(CustomerSegment.class));
	}

	static BranchProfile branch(Set<CustomerSegment> segments) {
		return new BranchProfile("JHB01", "Johannesburg Central",
				new Address("1 Main Rd", "Johannesburg", "Gauteng", "2001"), new GeoLocation(-26.2, 28.04), ZONE,
				segments);
	}

	static OpeningHours openingHours() {
		return new OpeningHours(OpeningHours.ID);
	}

	static WeeklyHours weekdays() {
		var ranges = List.of(TimeRange.of("08:00", "12:00"), TimeRange.of("13:00", "17:00"));
		return WeeklyHours.of(Map.of(DayOfWeek.MONDAY, ranges, DayOfWeek.TUESDAY, ranges));
	}

	static ServiceCategory category(CustomerSegment segment) {
		return ServiceCategory.define(ServiceCategoryId.random(), "Everyday banking", segment, null);
	}

	static Service service() {
		return service(category(CustomerSegment.PERSONAL));
	}

	static Service service(ServiceCategory category) {
		return Service.define(ServiceId.random(), "Consultation", category, "First visit", List.of(),
				Duration.ofMinutes(30));
	}

	static CapacityPool pool() {
		return CapacityPool.define(CapacityPoolId.random(), "Consultants", new Capacity(3));
	}

	static List<Object> events(AbstractAggregateRoot<?> aggregate) {
		return List.<Object>copyOf(AggregateEvents.of(aggregate));
	}

	static void clearEvents(AbstractAggregateRoot<?> aggregate) {
		AggregateEvents.clear(aggregate);
	}

	/**
	 * Asserts the aggregate registered exactly one event, of the given type, and returns it.
	 */
	static <T> T singleEvent(AbstractAggregateRoot<?> aggregate, Class<T> type) {

		var events = events(aggregate);

		assertThat(events).hasSize(1);
		assertThat(events.get(0)).isInstanceOf(type);

		return type.cast(events.get(0));
	}
}
