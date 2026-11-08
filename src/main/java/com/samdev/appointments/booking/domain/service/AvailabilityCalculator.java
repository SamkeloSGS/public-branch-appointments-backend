package com.samdev.appointments.booking.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.vo.CapacityProfile;
import com.samdev.appointments.booking.domain.vo.OfferingTerms;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Lists open start times. A candidate start is every multiple of the slot interval counted from the beginning of each
 * continuous run of bookable time. It is open when the appointment fits inside that run, the start is inside the
 * booking window, and the pool day would accept the reservation. Availability is a best-effort view; reserving is the
 * authority. Whether the branch and offering are active is checked by the caller.
 */
public final class AvailabilityCalculator {

	private AvailabilityCalculator() {}

	/**
	 * @param schedule the pool day, or {@code null} if none exists yet (then nothing is reserved)
	 * @param ignoreLeadTime staff may book inside the minimum lead time
	 */
	public static List<LocalTime> openTimes(LocalDate date, ZoneId zone, CapacityProfile profile,
			@Nullable PoolDaySchedule schedule, OfferingTerms terms, Instant now, boolean ignoreLeadTime) {
		return openTimes(date, zone, profile, schedule, terms, now, ignoreLeadTime, Integer.MAX_VALUE);
	}

	/**
	 * Whether {@link #openTimes} would list any time; stops at the first.
	 */
	public static boolean hasOpenTime(LocalDate date, ZoneId zone, CapacityProfile profile,
			@Nullable PoolDaySchedule schedule, OfferingTerms terms, Instant now, boolean ignoreLeadTime) {
		return !openTimes(date, zone, profile, schedule, terms, now, ignoreLeadTime, 1).isEmpty();
	}

	private static List<LocalTime> openTimes(LocalDate date, ZoneId zone, CapacityProfile profile,
			@Nullable PoolDaySchedule schedule, OfferingTerms terms, Instant now, boolean ignoreLeadTime, int limit) {

		var result = new ArrayList<LocalTime>();
		var duration = minutes(terms.duration());
		var interval = minutes(terms.slotInterval());

		for (var run : profile.bookableRuns()) {

			var end = minuteOfDay(run.end());

			for (var start = minuteOfDay(run.start()); start + duration <= end; start += interval) {

				var time = LocalTime.ofSecondOfDay(start * 60L);
				var range = new TimeRange(time, time.plusMinutes(duration));

				if (withinWindow(date, time, zone, terms, now, ignoreLeadTime)
						&& (schedule == null || schedule.canReserve(range))) {
					result.add(time);
					if (result.size() == limit) {
						return result;
					}
				}
			}
		}

		return result;
	}

	/**
	 * The start is in the future, no earlier than now plus the minimum lead time (unless ignored), and its date no
	 * later than today plus the maximum advance days, in the branch time zone.
	 */
	public static boolean withinWindow(LocalDate date, LocalTime start, ZoneId zone, OfferingTerms terms, Instant now,
			boolean ignoreLeadTime) {

		var startsAt = date.atTime(start).atZone(zone).toInstant();
		var today = LocalDate.ofInstant(now, zone);

		return startsAt.isAfter(now)
				&& (ignoreLeadTime || !startsAt.isBefore(now.plus(terms.minLeadTime())))
				&& !date.isAfter(today.plusDays(terms.maxAdvanceDays()));
	}

	/**
	 * Whether the start lies on the slot grid of the bookable run that contains the whole range.
	 */
	public static boolean onGrid(CapacityProfile profile, TimeRange range, Duration slotInterval) {
		return profile.runContaining(range)
				.map(run -> (minuteOfDay(range.start()) - minuteOfDay(run.start())) % minutes(slotInterval) == 0)
				.orElse(false);
	}

	private static int minuteOfDay(LocalTime time) {
		return time.toSecondOfDay() / 60;
	}

	private static int minutes(Duration duration) {
		return (int) duration.toMinutes();
	}
}
