package com.samdev.appointments.booking.domain.vo;

import java.time.LocalTime;
import java.util.Objects;

import org.jmolecules.ddd.types.ValueObject;

import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * A time range on a pool day with the number of customers who can be served at once (0 or more).
 */
public record CapacitySegment(LocalTime fromTime, LocalTime toTime, int capacity) implements ValueObject {

	public CapacitySegment {
		Objects.requireNonNull(fromTime, "From time is required");
		Objects.requireNonNull(toTime, "To time is required");
		if (!fromTime.isBefore(toTime)) {
			throw new IllegalArgumentException("A capacity segment starts before it ends");
		}
		if (capacity < 0) {
			throw new IllegalArgumentException("Capacity must not be negative");
		}
	}

	public static CapacitySegment of(TimeRange range, int capacity) {
		return new CapacitySegment(range.start(), range.end(), capacity);
	}

	public TimeRange range() {
		return new TimeRange(fromTime, toTime);
	}
}
