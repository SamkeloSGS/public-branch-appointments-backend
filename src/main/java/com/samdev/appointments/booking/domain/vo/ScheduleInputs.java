package com.samdev.appointments.booking.domain.vo;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * What the effective capacity rule needs for one pool on one date, read from the schedule projection.
 *
 * @param available whether the schedule projection has been built
 * @param hasException whether a calendar exception exists for the date
 * @param closed whether that exception is {@code CLOSED}
 * @param hours the applicable opening hours: the exception's special hours, else the weekly hours for the weekday
 * @param baseCapacity the pool's base capacity, 0 for an unknown pool
 * @param wholeDayCapacity a whole-day override, if any
 * @param windows windowed overrides
 */
public record ScheduleInputs(boolean available, boolean hasException, boolean closed, List<TimeRange> hours,
		int baseCapacity, @Nullable Integer wholeDayCapacity, List<WindowCapacity> windows) {

	public ScheduleInputs {
		hours = TimeRange.sortedWithoutOverlaps(hours);
		windows = List.copyOf(windows);
	}

	public static ScheduleInputs unavailable() {
		return new ScheduleInputs(false, false, false, List.of(), 0, null, List.of());
	}

	/**
	 * As if a {@code CLOSED} calendar exception were added for the date.
	 */
	public ScheduleInputs closedForTheDay() {
		return new ScheduleInputs(available, true, true, List.of(), baseCapacity, wholeDayCapacity, windows);
	}

	/**
	 * As if a {@code SPECIAL_HOURS} calendar exception with these hours were added for the date.
	 */
	public ScheduleInputs specialHours(List<TimeRange> specialHours) {
		return new ScheduleInputs(available, true, false, specialHours, baseCapacity, wholeDayCapacity, windows);
	}

	/**
	 * As if the weekly hours for this weekday changed. No effect on a date with a calendar exception.
	 */
	public ScheduleInputs weeklyHours(List<TimeRange> hoursForWeekday) {
		return hasException ? this
				: new ScheduleInputs(available, false, false, hoursForWeekday, baseCapacity, wholeDayCapacity,
						windows);
	}

	/**
	 * As if a capacity override were set: a whole-day one replaces the current whole-day override, a windowed one
	 * replaces the windowed overrides it overlaps.
	 */
	public ScheduleInputs overriding(@Nullable TimeRange window, int capacity) {

		if (window == null) {
			return new ScheduleInputs(available, hasException, closed, hours, baseCapacity, capacity, windows);
		}

		var replaced = new ArrayList<>(windows.stream().filter(it -> !it.window().overlaps(window)).toList());
		replaced.add(new WindowCapacity(window, capacity));

		return new ScheduleInputs(available, hasException, closed, hours, baseCapacity, wholeDayCapacity, replaced);
	}

	public record WindowCapacity(TimeRange window, int capacity) {}
}
