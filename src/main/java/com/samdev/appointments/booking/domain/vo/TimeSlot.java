package com.samdev.appointments.booking.domain.vo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

import jakarta.persistence.Column;

import org.jmolecules.ddd.types.ValueObject;

import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * The start and end of an appointment: local date and times in the branch time zone, and the same moments as
 * instants. Never spans midnight. Embedded in the appointment row.
 */
public record TimeSlot(@Column(name = "local_date") LocalDate date, LocalTime startTime, LocalTime endTime,
		Instant startsAt, Instant endsAt) implements ValueObject {

	public TimeSlot {
		Objects.requireNonNull(date, "Date is required");
		Objects.requireNonNull(startTime, "Start time is required");
		Objects.requireNonNull(endTime, "End time is required");
		Objects.requireNonNull(startsAt, "Start instant is required");
		Objects.requireNonNull(endsAt, "End instant is required");
		if (!startTime.isBefore(endTime) || !startsAt.isBefore(endsAt)) {
			throw new IllegalArgumentException("A time slot starts before it ends and never spans midnight");
		}
	}

	/**
	 * @throws IllegalArgumentException if the slot would span midnight
	 */
	public static TimeSlot of(LocalDate date, LocalTime start, Duration duration, ZoneId zone) {

		var endMinute = start.toSecondOfDay() / 60 + duration.toMinutes();
		if (duration.isNegative() || duration.isZero() || endMinute >= 24 * 60) {
			throw new IllegalArgumentException("A time slot starts before it ends and never spans midnight");
		}

		var end = start.plus(duration);

		return new TimeSlot(date, start, end, date.atTime(start).atZone(zone).toInstant(),
				date.atTime(end).atZone(zone).toInstant());
	}

	public TimeRange range() {
		return new TimeRange(startTime, endTime);
	}

	public Duration duration() {
		return Duration.between(startTime, endTime);
	}

	/**
	 * The local date of the moment at the branch, using the slot's own offset from UTC.
	 */
	public LocalDate localDateOf(Instant moment) {

		var offsetSeconds = date.atTime(startTime).toEpochSecond(ZoneOffset.UTC) - startsAt.getEpochSecond();

		return LocalDate.ofInstant(moment, ZoneOffset.ofTotalSeconds((int) offsetSeconds));
	}
}
