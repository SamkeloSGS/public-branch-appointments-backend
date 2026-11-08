package com.samdev.appointments.branchmanagement.domain.vo;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.jmolecules.ddd.types.ValueObject;

import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * One row of a branch's weekly hours: a day of week and a time range on that day.
 */
public record OpeningPeriod(@Enumerated(EnumType.STRING) DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt)
		implements ValueObject {

	public OpeningPeriod {
		Objects.requireNonNull(dayOfWeek, "Day of week must not be null");
		new TimeRange(opensAt, closesAt);
	}

	public OpeningPeriod(DayOfWeek dayOfWeek, TimeRange range) {
		this(dayOfWeek, range.start(), range.end());
	}

	public TimeRange range() {
		return new TimeRange(opensAt, closesAt);
	}
}
