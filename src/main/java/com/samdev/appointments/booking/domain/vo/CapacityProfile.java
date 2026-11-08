package com.samdev.appointments.booking.domain.vo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * All capacity segments of one pool day, ordered and non-overlapping. A moment no segment covers has capacity 0.
 * Bookable time is time with a capacity of at least 1. Not persisted on its own, so not a jMolecules value object.
 */
public record CapacityProfile(List<CapacitySegment> segments) {

	public static final CapacityProfile EMPTY = new CapacityProfile(List.of());

	public CapacityProfile {
		segments = segments.stream().sorted(Comparator.comparing(CapacitySegment::fromTime)).toList();
		for (var i = 1; i < segments.size(); i++) {
			if (segments.get(i - 1).range().overlaps(segments.get(i).range())) {
				throw new IllegalArgumentException("Capacity segments must not overlap");
			}
		}
	}

	public int capacityAt(LocalTime moment) {
		return segments.stream()
				.filter(it -> it.range().contains(moment))
				.mapToInt(CapacitySegment::capacity)
				.findFirst()
				.orElse(0);
	}

	/**
	 * Maximal continuous runs of bookable time.
	 */
	public List<TimeRange> bookableRuns() {

		var runs = new ArrayList<TimeRange>();
		LocalTime start = null;
		LocalTime end = null;

		for (var segment : segments) {
			if (segment.capacity() < 1) {
				continue;
			}
			if (start != null && end.equals(segment.fromTime())) {
				end = segment.toTime();
			} else {
				if (start != null) {
					runs.add(new TimeRange(start, end));
				}
				start = segment.fromTime();
				end = segment.toTime();
			}
		}
		if (start != null) {
			runs.add(new TimeRange(start, end));
		}

		return runs;
	}

	/**
	 * The bookable run containing the range entirely, if any.
	 */
	public java.util.Optional<TimeRange> runContaining(TimeRange range) {
		return bookableRuns().stream()
				.filter(it -> !range.start().isBefore(it.start()) && !range.end().isAfter(it.end()))
				.findFirst();
	}

	public boolean isEmpty() {
		return segments.isEmpty();
	}
}
