package com.samdev.appointments.booking.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.vo.CapacityProfile;
import com.samdev.appointments.booking.domain.vo.CapacitySegment;
import com.samdev.appointments.booking.domain.vo.OfferingTerms;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * The open-time rule: slot grid per bookable run, fitting inside it, booking window, and reservations.
 */
class AvailabilityCalculatorTests {

	static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
	static final LocalDate DATE = LocalDate.parse("2026-10-14");
	static final Instant DAY_BEFORE = Instant.parse("2026-10-13T08:00:00Z");
	static final OfferingTerms TERMS = OfferingTerms.ofMinutes(30, 15, 0, 30);

	@Test
	@DisplayName("BK-1 starts every slot interval from the beginning of each bookable run, fitting inside it")
	void gridPerRun() {

		var profile = profile(segment("08:00", "09:00", 1), segment("09:00", "09:10", 0), segment("09:10", "10:00", 1));

		assertThat(open(profile, null, TERMS, DAY_BEFORE)).extracting(LocalTime::toString)
				.containsExactly("08:00", "08:15", "08:30", "09:10", "09:25");
	}

	@Test
	@DisplayName("BK-1 a closed day has no open times")
	void closedDay() {
		assertThat(open(CapacityProfile.EMPTY, null, TERMS, DAY_BEFORE)).isEmpty();
	}

	@Test
	@DisplayName("BK-1 the minimum lead time and maximum advance days apply in the branch time zone")
	void bookingWindow() {

		var profile = profile(segment("08:00", "10:00", 1));
		var at0815 = Instant.parse("2026-10-14T06:15:00Z");

		assertThat(open(profile, null, OfferingTerms.ofMinutes(30, 30, 60, 30), at0815))
				.extracting(LocalTime::toString).containsExactly("09:30");
		assertThat(open(profile, null, OfferingTerms.ofMinutes(30, 30, 60, 30), at0815, true))
				.as("staff ignore the lead time, not the past").extracting(LocalTime::toString)
				.containsExactly("08:30", "09:00", "09:30");
		assertThat(open(profile, null, OfferingTerms.ofMinutes(30, 30, 0, 1),
				Instant.parse("2026-10-12T21:00:00Z"))).as("23:00 on the 12th: the 14th is 2 days ahead").isEmpty();
		assertThat(open(profile, null, OfferingTerms.ofMinutes(30, 30, 0, 1),
				Instant.parse("2026-10-12T22:30:00Z"))).as("00:30 on the 13th: the 14th is 1 day ahead").isNotEmpty();
	}

	@Test
	@DisplayName("BK-1 times the pool day would not accept are not open")
	void reservations() {

		var profile = profile(segment("08:00", "09:00", 1));
		var schedule = PoolDaySchedule.open(CapacityPoolId.random(), DATE, profile);
		schedule.reserve(AppointmentId.random(), TimeRange.of("08:15", "08:45"));

		assertThat(open(profile, schedule, TERMS, DAY_BEFORE)).isEmpty();

		var longer = PoolDaySchedule.open(CapacityPoolId.random(), DATE,
				profile(segment("08:00", "10:00", 1)));
		longer.reserve(AppointmentId.random(), TimeRange.of("08:30", "09:00"));
		assertThat(open(profile(segment("08:00", "10:00", 1)), longer, TERMS, DAY_BEFORE))
				.extracting(LocalTime::toString).containsExactly("08:00", "09:00", "09:15", "09:30");
	}

	@Test
	@DisplayName("BK-4 onGrid accepts only starts on the grid of the run containing the appointment")
	void onGrid() {

		var profile = profile(segment("08:10", "12:00", 2));

		assertThat(AvailabilityCalculator.onGrid(profile, TimeRange.of("08:25", "08:55"), Duration.ofMinutes(15))).isTrue();
		assertThat(AvailabilityCalculator.onGrid(profile, TimeRange.of("08:30", "09:00"), Duration.ofMinutes(15))).isFalse();
		assertThat(AvailabilityCalculator.onGrid(profile, TimeRange.of("11:40", "12:10"), Duration.ofMinutes(15))).isFalse();
	}

	static List<LocalTime> open(CapacityProfile profile, PoolDaySchedule schedule, OfferingTerms terms, Instant now) {
		return open(profile, schedule, terms, now, false);
	}

	static List<LocalTime> open(CapacityProfile profile, PoolDaySchedule schedule, OfferingTerms terms, Instant now,
			boolean staff) {
		return AvailabilityCalculator.openTimes(DATE, ZONE, profile, schedule, terms, now, staff);
	}

	static CapacityProfile profile(CapacitySegment... segments) {
		return new CapacityProfile(List.of(segments));
	}

	static CapacitySegment segment(String from, String to, int capacity) {
		return new CapacitySegment(LocalTime.parse(from), LocalTime.parse(to), capacity);
	}
}
