package com.samdev.appointments.booking.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.OverbookingResolved;
import com.samdev.appointments.booking.PoolDayOverbooked;
import com.samdev.appointments.booking.domain.event.ReservationDisplaced;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.vo.CapacityProfile;
import com.samdev.appointments.booking.domain.vo.CapacitySegment;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Reserving, releasing and applying capacity on one pool day, including half-open boundaries and mixed durations.
 */
class PoolDayScheduleTests {

	static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z");
	static final LocalDate DATE = LocalDate.parse("2026-10-14");

	@Test
	@DisplayName("BK-3 reserve accepts while load is below capacity at every moment, then refuses")
	void reservesUpToCapacity() {

		var schedule = schedule(profile("08:00-17:00=2"));

		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));
		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));

		assertSlotUnavailable(() -> schedule.reserve(AppointmentId.random(), range("09:15", "09:45")));
		assertThat(schedule.loadAt(LocalTime.parse("09:15"))).isEqualTo(2);
		assertThat(schedule.canReserve(range("09:30", "10:00"))).as("half-open: 09:30 is free again").isTrue();
		assertThat(schedule.canReserve(range("08:30", "09:00"))).isTrue();
	}

	@Test
	@DisplayName("BK-3 offerings of different durations sharing a pool block each other wherever they overlap")
	void mixedDurations() {

		var schedule = schedule(profile("08:00-17:00=1"));

		schedule.reserve(AppointmentId.random(), range("09:00", "10:00"));

		assertThat(schedule.canReserve(range("09:45", "10:00"))).isFalse();
		assertThat(schedule.canReserve(range("08:45", "09:15"))).isFalse();
		assertThat(schedule.canReserve(range("08:30", "09:00"))).isTrue();
		assertThat(schedule.canReserve(range("10:00", "10:15"))).isTrue();
	}

	@Test
	@DisplayName("BK-3 a reservation needs capacity at every moment: segments with less capacity or no segment refuse")
	void respectsSegments() {

		var schedule = schedule(profile("08:00-12:00=2", "12:00-13:00=0", "13:00-17:00=1"));

		assertThat(schedule.canReserve(range("11:30", "12:30"))).as("crosses a zero segment").isFalse();
		assertThat(schedule.canReserve(range("16:45", "17:15"))).as("runs past closing").isFalse();
		assertThat(schedule.canReserve(range("07:45", "08:15"))).as("starts before opening").isFalse();

		schedule.reserve(AppointmentId.random(), range("13:00", "14:00"));
		assertThat(schedule.canReserve(range("13:30", "14:30"))).isFalse();
		assertThat(schedule.canReserve(range("11:00", "12:00"))).isTrue();
	}

	@Test
	@DisplayName("BK-3 an appointment holds at most one reservation")
	void oneReservationPerAppointment() {

		var schedule = schedule(profile("08:00-17:00=5"));
		var id = AppointmentId.random();
		schedule.reserve(id, range("09:00", "09:30"));

		assertThatExceptionOfType(IllegalStateException.class)
				.isThrownBy(() -> schedule.reserve(id, range("10:00", "10:30")));
	}

	@Test
	@DisplayName("BK-6 release frees the place immediately")
	void release() {

		var schedule = schedule(profile("08:00-17:00=1"));
		var id = AppointmentId.random();
		schedule.reserve(id, range("09:00", "09:30"));

		schedule.release(id, NOW);
		schedule.release(id, NOW);

		assertThat(schedule.canReserve(range("09:00", "09:30"))).isTrue();
		assertThat(events(schedule)).isEmpty();
	}

	@Test
	@DisplayName("BK-9 reducing capacity below the load overbooks once; nothing is displaced; restoring resolves once")
	void overbooking() {

		var schedule = schedule(profile("08:00-17:00=2"));
		var first = AppointmentId.random();
		schedule.reserve(first, range("09:00", "09:30"));
		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));

		schedule.applyCapacity(profile("08:00-17:00=1"), NOW);
		schedule.applyCapacity(profile("08:00-17:00=1"), NOW);

		assertThat(schedule.isOverbooked()).isTrue();
		assertThat(schedule.getReservations()).hasSize(2);
		assertThat(schedule.overbookedIntervals()).containsExactly(range("09:00", "09:30"));
		assertThat(events(schedule)).singleElement().isInstanceOf(PoolDayOverbooked.class);
		assertThat(schedule.canReserve(range("09:15", "09:45"))).as("no new reservation in the interval").isFalse();
		assertThat(schedule.canReserve(range("10:00", "10:30"))).isTrue();

		schedule.release(first, NOW);

		assertThat(schedule.isOverbooked()).isFalse();
		assertThat(events(schedule)).hasSize(2).last().isInstanceOf(OverbookingResolved.class);
	}

	@Test
	@DisplayName("BK-9 restoring capacity resolves overbooking")
	void restoringCapacityResolves() {

		var schedule = schedule(profile("08:00-17:00=2"));
		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));
		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));
		schedule.applyCapacity(profile("08:00-17:00=1"), NOW);

		schedule.applyCapacity(profile("08:00-17:00=2"), NOW);

		assertThat(schedule.isOverbooked()).isFalse();
		assertThat(events(schedule)).extracting(Object::getClass)
				.containsExactly((Class) PoolDayOverbooked.class, OverbookingResolved.class);
	}

	@Test
	@DisplayName("BK-10 zero capacity at any moment of a reservation displaces it; others stay")
	void displacement() {

		var schedule = schedule(profile("08:00-17:00=2"));
		var morning = AppointmentId.random();
		var lunch = AppointmentId.random();
		schedule.reserve(morning, range("09:00", "09:30"));
		schedule.reserve(lunch, range("11:45", "12:15"));

		schedule.applyCapacity(profile("08:00-12:00=2", "13:00-17:00=2"), NOW);

		assertThat(schedule.holds(morning)).isTrue();
		assertThat(schedule.holds(lunch)).isFalse();
		assertThat(events(schedule)).singleElement().isInstanceOfSatisfying(ReservationDisplaced.class, it -> {
			assertThat(it.appointmentId()).isEqualTo(lunch);
			assertThat(it.fromTime()).isEqualTo(LocalTime.parse("11:45"));
			assertThat(it.date()).isEqualTo(DATE);
		});
		assertThat(schedule.isOverbooked()).isFalse();
	}

	@Test
	@DisplayName("BK-10 an empty profile (closed or suspended) displaces every reservation")
	void closureDisplacesAll() {

		var schedule = schedule(profile("08:00-17:00=3"));
		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));
		schedule.reserve(AppointmentId.random(), range("15:00", "16:00"));

		schedule.applyCapacity(CapacityProfile.EMPTY, NOW);

		assertThat(schedule.getReservations()).isEmpty();
		assertThat(events(schedule)).hasSize(2).allMatch(ReservationDisplaced.class::isInstance);
	}

	@Test
	@DisplayName("BK-12 impactOf predicts exactly what applyCapacity does, without changing anything")
	void impactMatchesApply() {

		var schedule = schedule(profile("08:00-17:00=2"));
		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));
		schedule.reserve(AppointmentId.random(), range("09:00", "09:30"));
		schedule.reserve(AppointmentId.random(), range("10:00", "10:30"));
		schedule.reserve(AppointmentId.random(), range("10:00", "10:30"));
		schedule.reserve(AppointmentId.random(), range("16:00", "16:30"));

		var proposed = profile("08:00-12:00=1", "12:00-15:00=2");
		var impact = schedule.impactOf(proposed);

		assertThat(impact.displaced()).hasSize(1);
		assertThat(impact.overbookedIntervals()).containsExactly(range("09:00", "09:30"), range("10:00", "10:30"));
		assertThat(schedule.getReservations()).hasSize(5);
		assertThat(events(schedule)).isEmpty();

		schedule.applyCapacity(proposed, NOW);

		assertThat(schedule.getReservations()).hasSize(4);
		assertThat(schedule.overbookedIntervals()).isEqualTo(impact.overbookedIntervals());
	}

	static PoolDaySchedule schedule(CapacityProfile profile) {
		return PoolDaySchedule.open(CapacityPoolId.random(), DATE, profile);
	}

	/**
	 * Segments written as {@code "08:00-12:00=2"}.
	 */
	static CapacityProfile profile(String... segments) {
		return new CapacityProfile(List.of(segments).stream().map(it -> {
			var parts = it.split("[-=]");
			return new CapacitySegment(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]), Integer.parseInt(parts[2]));
		}).toList());
	}

	static TimeRange range(String start, String end) {
		return TimeRange.of(start, end);
	}

	static void assertSlotUnavailable(Runnable action) {
		assertThatExceptionOfType(BookingRejected.class).isThrownBy(action::run)
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(RejectionCode.SLOT_UNAVAILABLE));
	}

	static List<Object> events(Object aggregate) {
		Collection<?> events = ReflectionTestUtils.invokeMethod(aggregate, "domainEvents");
		return List.copyOf(events);
	}
}
