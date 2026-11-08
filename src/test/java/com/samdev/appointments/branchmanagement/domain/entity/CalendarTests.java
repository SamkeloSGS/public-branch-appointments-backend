package com.samdev.appointments.branchmanagement.domain.entity;

import static com.samdev.appointments.branchmanagement.domain.entity.DomainFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.branchmanagement.CalendarExceptionAdded;
import com.samdev.appointments.branchmanagement.CalendarExceptionId;
import com.samdev.appointments.branchmanagement.CalendarExceptionRemoved;
import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.CapacityOverrideRemoved;
import com.samdev.appointments.branchmanagement.CapacityOverrideSet;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.OverrideReason;
import com.samdev.appointments.branchmanagement.TimeRange;

class CalendarTests {

	static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

	CapacityPool pool = pool();

	// Calendar exceptions

	@Test
	@DisplayName("BM-7 a CLOSED exception for today registers CalendarExceptionAdded")
	void addsClosedException() {

		var exception = closed(TODAY);

		var event = singleEvent(exception, CalendarExceptionAdded.class);
		assertThat(event.type()).isEqualTo(ExceptionType.CLOSED);
		assertThat(event.hours()).isEmpty();
		assertThat(event.date()).isEqualTo(TODAY);
	}

	@Test
	@DisplayName("BM-7 a date in the past is rejected")
	void rejectsPastDate() {
		assertThatIllegalArgumentException().isThrownBy(() -> closed(TODAY.minusDays(1)));
	}

	@Test
	@DisplayName("BM-7 SPECIAL_HOURS needs at least one valid non-overlapping range")
	void validatesSpecialHours() {

		assertThatIllegalArgumentException().isThrownBy(() -> special(List.of()));
		assertThatIllegalArgumentException().isThrownBy(
				() -> special(List.of(TimeRange.of("09:00", "12:00"), TimeRange.of("11:00", "13:00"))));

		var exception = special(List.of(TimeRange.of("13:00", "15:00"), TimeRange.of("09:00", "12:00")));
		assertThat(exception.getHours()).extracting(TimeRange::start).isSorted();
	}

	@Test
	void closedExceptionHasNoHours() {
		assertThatIllegalArgumentException().isThrownBy(() -> CalendarException.add(CalendarExceptionId.random(),
				TODAY, ExceptionType.CLOSED, List.of(TimeRange.of("09:00", "12:00")), null, TODAY));
	}

	@Test
	void removalRegistersCalendarExceptionRemoved() {

		var exception = closed(TODAY.plusDays(3));
		clearEvents(exception);

		exception.remove();

		assertThat(singleEvent(exception, CalendarExceptionRemoved.class).date()).isEqualTo(TODAY.plusDays(3));
	}

	// Capacity overrides

	@Test
	@DisplayName("BM-8 setting an override registers CapacityOverrideSet; 0 and above-base capacities are accepted")
	void setsOverride() {

		var zero = override(null, 0);
		var event = singleEvent(zero, CapacityOverrideSet.class);
		assertThat(event.capacity()).isZero();
		assertThat(event.window()).isNull();
		assertThat(event.poolId()).isEqualTo(pool.getId());

		assertThat(override(TimeRange.of("09:00", "12:00"), 10).getCapacity()).isEqualTo(10);
	}

	@Test
	@DisplayName("BM-8 negative capacity and past dates are rejected")
	void rejectsInvalidOverrides() {

		assertThatIllegalArgumentException().isThrownBy(() -> override(null, -1));
		assertThatIllegalArgumentException().isThrownBy(() -> CapacityOverride.set(CapacityOverrideId.random(), pool,
				TODAY.minusDays(1), null, 1, OverrideReason.LEAVE, null, TODAY));
	}

	@Test
	@DisplayName("BM-8 conflicts: two whole-day, or overlapping windows; whole-day and windowed coexist")
	void detectsConflicts() {

		var wholeDay = override(null, 1);
		var morning = override(TimeRange.of("08:00", "12:00"), 1);

		assertThat(override(null, 2).conflictsWith(wholeDay)).isTrue();
		assertThat(morning.conflictsWith(wholeDay)).isFalse();
		assertThat(override(TimeRange.of("11:00", "13:00"), 1).conflictsWith(morning)).isTrue();
		assertThat(override(TimeRange.of("12:00", "13:00"), 1).conflictsWith(morning)).isFalse();

		var otherDay = CapacityOverride.set(CapacityOverrideId.random(), pool, TODAY.plusDays(2), null, 1,
				OverrideReason.LEAVE, null, TODAY);
		assertThat(otherDay.conflictsWith(wholeDay)).isFalse();
	}

	@Test
	@DisplayName("BM-8 removing an override registers CapacityOverrideRemoved")
	void removesOverride() {

		var override = override(null, 1);
		clearEvents(override);

		override.remove();

		assertThat(singleEvent(override, CapacityOverrideRemoved.class).poolId()).isEqualTo(pool.getId());
	}

	private CalendarException closed(LocalDate date) {
		return CalendarException.add(CalendarExceptionId.random(), date, ExceptionType.CLOSED, List.of(),
				"Public holiday", TODAY);
	}

	private CalendarException special(List<TimeRange> hours) {
		return CalendarException.add(CalendarExceptionId.random(), TODAY.plusDays(1),
				ExceptionType.SPECIAL_HOURS, hours, null, TODAY);
	}

	private CapacityOverride override(TimeRange window, int capacity) {
		return CapacityOverride.set(CapacityOverrideId.random(), pool, TODAY.plusDays(1), window, capacity,
				OverrideReason.SHORT_STAFFED, null, TODAY);
	}
}
