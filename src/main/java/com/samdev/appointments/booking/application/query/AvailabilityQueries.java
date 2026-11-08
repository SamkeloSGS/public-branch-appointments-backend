package com.samdev.appointments.booking.application.query;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.application.exception.OfferingNotFoundException;
import com.samdev.appointments.booking.application.port.BookingSettings;
import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.booking.application.port.ScheduleProjection.OfferingView;
import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.repository.PoolDaySchedules;
import com.samdev.appointments.booking.domain.service.AvailabilityCalculator;
import com.samdev.appointments.booking.domain.service.CapacityProfileCalculator;
import com.samdev.appointments.branchmanagement.ServiceId;

/**
 * Open times for the public. Uses the stored pool day where one exists, else a freshly computed profile with no
 * reservations. Creates nothing.
 */
@Component
@Transactional(readOnly = true)
public class AvailabilityQueries {

	private final ScheduleProjection projection;
	private final PoolDaySchedules schedules;
	private final BookingSettings settings;
	private final Clock clock;

	AvailabilityQueries(ScheduleProjection projection, PoolDaySchedules schedules, BookingSettings settings,
			Clock clock) {

		this.projection = projection;
		this.schedules = schedules;
		this.settings = settings;
		this.clock = clock;
	}

	/**
	 * Open start times on one day, in order; empty when the branch is closed that day.
	 *
	 * @throws OfferingNotFoundException when the branch does not offer the service
	 */
	public List<LocalTime> openTimes(ServiceId serviceId, LocalDate date) {
		return openTimes(activeOffering(serviceId), date);
	}

	/**
	 * Dates in the range with at least one open time.
	 *
	 * @throws IllegalArgumentException when the range is reversed or longer than the configured maximum
	 */
	public List<LocalDate> openDays(ServiceId serviceId, LocalDate from, LocalDate to) {

		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > settings.maxAvailabilityDays()) {
			throw new IllegalArgumentException(
					"The range must run forwards and cover at most " + settings.maxAvailabilityDays() + " days");
		}

		var offering = activeOffering(serviceId);

		// The whole range in a few queries, then each day in memory
		var stored = schedules.findByCapacityPoolIdAndDateBetween(offering.poolId().value(), from, to).stream()
				.collect(Collectors.toMap(PoolDaySchedule::getDate, Function.identity()));
		var inputs = projection.inputs(offering.poolId(), from, to);
		var now = clock.instant();
		var result = new ArrayList<LocalDate>();

		for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {

			var schedule = stored.get(date);
			var profile = schedule != null ? schedule.profile() : CapacityProfileCalculator.profile(inputs.get(date));

			if (AvailabilityCalculator.hasOpenTime(date, projection.timeZone(), profile, schedule, offering.terms(), now,
					false)) {
				result.add(date);
			}
		}

		return result;
	}

	private List<LocalTime> openTimes(OfferingView offering, LocalDate date) {

		var schedule = schedules.findByCapacityPoolIdAndDate(offering.poolId().value(), date).orElse(null);
		var profile = schedule != null ? schedule.profile()
				: CapacityProfileCalculator.profile(projection.inputs(offering.poolId(), date));

		return AvailabilityCalculator.openTimes(date, projection.timeZone(), profile, schedule, offering.terms(),
				clock.instant(), false);
	}

	private OfferingView activeOffering(ServiceId serviceId) {
		return projection.offering(serviceId).filter(OfferingView::active)
				.orElseThrow(OfferingNotFoundException::new);
	}
}
