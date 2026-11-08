package com.samdev.appointments.branchmanagement;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jmolecules.ddd.types.ValueObject;

/**
 * A half-open range of local times {@code [start, end)} within one day, in the branch's time zone.
 */
public record TimeRange(LocalTime start, LocalTime end) implements ValueObject {

	public static final Comparator<TimeRange> BY_START = Comparator.comparing(TimeRange::start);

	public TimeRange {
		Objects.requireNonNull(start, "Start time must not be null");
		Objects.requireNonNull(end, "End time must not be null");
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("Time range start " + start + " must be before end " + end);
		}
	}

	public static TimeRange of(String start, String end) {
		return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
	}

	public boolean overlaps(TimeRange other) {
		return start.isBefore(other.end) && other.start.isBefore(end);
	}

	public boolean contains(LocalTime time) {
		return !time.isBefore(start) && time.isBefore(end);
	}

	/**
	 * Returns the ranges sorted by start time.
	 *
	 * @throws IllegalArgumentException if any two ranges overlap
	 */
	public static List<TimeRange> sortedWithoutOverlaps(List<TimeRange> ranges) {
		var sorted = ranges.stream().sorted(BY_START).toList();
		for (int i = 1; i < sorted.size(); i++) {
			if (sorted.get(i - 1).overlaps(sorted.get(i))) {
				throw new IllegalArgumentException("Time ranges " + sorted.get(i - 1) + " and " + sorted.get(i) + " overlap");
			}
		}
		return sorted;
	}

	@Override
	public String toString() {
		return start + "-" + end;
	}
}
