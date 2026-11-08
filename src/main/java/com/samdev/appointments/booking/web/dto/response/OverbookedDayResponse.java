package com.samdev.appointments.booking.web.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.samdev.appointments.booking.application.query.AppointmentQueries.OverbookedDay;

/**
 * An overbooked pool day with, for each overbooked interval, the appointments overlapping it. A branch manager chooses
 * which of them to move.
 */
public record OverbookedDayResponse(UUID poolId, LocalDate date, List<Interval> intervals) {

	public static OverbookedDayResponse of(OverbookedDay day) {
		return new OverbookedDayResponse(day.poolId().value(), day.date(), day.intervals().stream()
				.map(it -> new Interval(it.range().start(), it.range().end(), it.capacity(), it.load(),
						it.appointments().stream().map(AppointmentResponse::of).toList()))
				.toList());
	}

	public record Interval(LocalTime start, LocalTime end, int capacity, int load,
			List<AppointmentResponse> appointments) {}
}
