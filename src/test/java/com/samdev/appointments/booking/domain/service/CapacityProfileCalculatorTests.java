package com.samdev.appointments.booking.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.booking.domain.vo.CapacitySegment;
import com.samdev.appointments.booking.domain.vo.ScheduleInputs;
import com.samdev.appointments.booking.domain.vo.ScheduleInputs.WindowCapacity;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Each step of the effective capacity rule (Branch management spec 5.7).
 */
class CapacityProfileCalculatorTests {

	static final List<TimeRange> HOURS = List.of(TimeRange.of("08:00", "12:00"), TimeRange.of("13:00", "17:00"));

	@Test
	@DisplayName("BK-8 rule 6: within hours, the base capacity")
	void baseCapacity() {
		assertThat(segments(inputs(true, false, HOURS, null, List.of()))).containsExactly(
				segment("08:00", "12:00", 2), segment("13:00", "17:00", 2));
	}

	@Test
	@DisplayName("BK-8 rule 1: before the schedule projection is built nothing is bookable")
	void projectionNotBuilt() {
		assertThat(segments(inputs(false, false, HOURS, null, List.of()))).isEmpty();
	}

	@Test
	@DisplayName("BK-8 rule 2: a CLOSED exception means capacity 0 all day")
	void closed() {
		assertThat(segments(inputs(true, false, HOURS, null, List.of()).closedForTheDay())).isEmpty();
	}

	@Test
	@DisplayName("BK-8 rule 3: special hours replace weekly hours; outside them nothing is bookable")
	void specialHours() {
		assertThat(segments(inputs(true, false, HOURS, null, List.of()).specialHours(List.of(TimeRange.of("09:00", "11:00")))))
				.containsExactly(segment("09:00", "11:00", 2));
	}

	@Test
	@DisplayName("BK-8 rules 4 and 5: a windowed override wins over a whole-day override, which wins over the base")
	void overrides() {

		var inputs = inputs(true, false, HOURS, 4,
				List.of(new WindowCapacity(TimeRange.of("10:00", "11:00"), 0),
						new WindowCapacity(TimeRange.of("11:30", "14:00"), 1)));

		assertThat(segments(inputs)).containsExactly(
				segment("08:00", "10:00", 4), segment("10:00", "11:00", 0), segment("11:00", "11:30", 4),
				segment("11:30", "12:00", 1), segment("13:00", "14:00", 1), segment("14:00", "17:00", 4));
	}

	@Test
	@DisplayName("BK-8 adjacent hours with the same capacity merge into one segment")
	void merges() {
		assertThat(segments(inputs(true, false,
				List.of(TimeRange.of("08:00", "12:00"), TimeRange.of("12:00", "17:00")), null, List.of())))
				.containsExactly(segment("08:00", "17:00", 2));
	}

	static ScheduleInputs inputs(boolean active, boolean closed, List<TimeRange> hours, Integer wholeDay,
			List<WindowCapacity> windows) {
		return new ScheduleInputs(active, closed, closed, hours, 2, wholeDay, windows);
	}

	static List<CapacitySegment> segments(ScheduleInputs inputs) {
		return CapacityProfileCalculator.profile(inputs).segments();
	}

	static CapacitySegment segment(String from, String to, int capacity) {
		return new CapacitySegment(LocalTime.parse(from), LocalTime.parse(to), capacity);
	}
}
