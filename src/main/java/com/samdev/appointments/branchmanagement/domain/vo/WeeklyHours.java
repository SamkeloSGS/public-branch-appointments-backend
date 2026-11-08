package com.samdev.appointments.branchmanagement.domain.vo;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * The recurring times a branch accepts appointments: day of week to ordered, non-overlapping time ranges.
 * A day without ranges means the branch is closed that weekday. A value object, but deliberately not a
 * jMolecules {@code ValueObject}: it is never persisted as such (see {@link OpeningPeriod}).
 */
public final class WeeklyHours {

	private final Map<DayOfWeek, List<TimeRange>> days;

	private WeeklyHours(Map<DayOfWeek, List<TimeRange>> days) {
		this.days = days;
	}

	/**
	 * @throws IllegalArgumentException if ranges overlap within a day
	 */
	public static WeeklyHours of(Map<DayOfWeek, List<TimeRange>> source) {
		var days = new EnumMap<DayOfWeek, List<TimeRange>>(DayOfWeek.class);
		for (var day : DayOfWeek.values()) {
			var ranges = source == null ? null : source.get(day);
			days.put(day, ranges == null ? List.of() : TimeRange.sortedWithoutOverlaps(ranges));
		}
		return new WeeklyHours(Collections.unmodifiableMap(days));
	}

	public static WeeklyHours none() {
		return of(Map.of());
	}

	public static WeeklyHours fromPeriods(Collection<OpeningPeriod> periods) {
		return of(periods.stream().collect(Collectors.groupingBy(OpeningPeriod::dayOfWeek,
				Collectors.mapping(OpeningPeriod::range, Collectors.toList()))));
	}

	public List<OpeningPeriod> toPeriods() {
		return days.entrySet().stream()
				.flatMap(it -> it.getValue().stream().map(range -> new OpeningPeriod(it.getKey(), range)))
				.toList();
	}

	public List<TimeRange> on(DayOfWeek day) {
		return days.get(day);
	}

	public boolean isEmpty() {
		return days.values().stream().allMatch(List::isEmpty);
	}

	/**
	 * Every day of the week mapped to its ranges; closed days map to an empty list.
	 */
	public Map<DayOfWeek, List<TimeRange>> asMap() {
		return days;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof WeeklyHours other && days.equals(other.days);
	}

	@Override
	public int hashCode() {
		return days.hashCode();
	}

	@Override
	public String toString() {
		return days.toString();
	}
}
