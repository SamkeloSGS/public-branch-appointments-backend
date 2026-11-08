package com.samdev.appointments.booking.application.query;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.repository.PoolDaySchedules;
import com.samdev.appointments.booking.domain.service.CapacityProfileCalculator;
import com.samdev.appointments.booking.domain.vo.ScheduleInputs;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * What a proposed closure, hours change or capacity override would do to existing bookings: the number of
 * appointments that would be displaced and of intervals that would be overbooked. Uses the same calculation as the
 * real change, on the pool days that exist from today on (days without bookings are unaffected). Changes nothing and
 * publishes nothing.
 */
@Component
@Transactional(readOnly = true)
public class ImpactPreview {

	private final PoolDaySchedules schedules;
	private final ScheduleProjection projection;
	private final Clock clock;

	ImpactPreview(PoolDaySchedules schedules, ScheduleProjection projection, Clock clock) {
		this.schedules = schedules;
		this.projection = projection;
		this.clock = clock;
	}

	public Impact preview(ProposedChange change) {

		var today = LocalDate.ofInstant(clock.instant(), projection.timeZone());

		var displaced = 0;
		var overbooked = 0;

		for (var schedule : schedules.findByDateGreaterThanEqual(today)) {

			if (!change.affects(schedule)) {
				continue;
			}

			var inputs = change.apply(schedule.getDate())
					.apply(projection.inputs(schedule.getCapacityPoolId(), schedule.getDate()));
			var impact = schedule.impactOf(CapacityProfileCalculator.profile(inputs));

			displaced += impact.displaced().size();
			overbooked += impact.overbookedIntervals().size();
		}

		return new Impact(displaced, overbooked);
	}

	public record Impact(int displacedAppointments, int overbookedIntervals) {}

	/**
	 * A change Branch management is about to make.
	 */
	public sealed interface ProposedChange {

		boolean affects(PoolDaySchedule schedule);

		UnaryOperator<ScheduleInputs> apply(LocalDate date);
	}

	/**
	 * A {@code CLOSED} or {@code SPECIAL_HOURS} calendar exception on one date, for every pool of the branch.
	 */
	public record CalendarExceptionChange(LocalDate date, ExceptionType type, List<TimeRange> hours)
			implements ProposedChange {

		@Override
		public boolean affects(PoolDaySchedule schedule) {
			return schedule.getDate().equals(date);
		}

		@Override
		public UnaryOperator<ScheduleInputs> apply(LocalDate on) {
			return type == ExceptionType.CLOSED ? ScheduleInputs::closedForTheDay : it -> it.specialHours(hours);
		}
	}

	/**
	 * A capacity override for one pool on one date; a {@code null} window means the whole day.
	 */
	public record CapacityOverrideChange(CapacityPoolId poolId, LocalDate date, @Nullable TimeRange window,
			int capacity) implements ProposedChange {

		@Override
		public boolean affects(PoolDaySchedule schedule) {
			return schedule.getDate().equals(date) && schedule.getCapacityPoolId().equals(poolId);
		}

		@Override
		public UnaryOperator<ScheduleInputs> apply(LocalDate on) {
			return it -> it.overriding(window, capacity);
		}
	}

	/**
	 * New weekly hours for the branch; dates with a calendar exception keep their exception.
	 */
	public record WeeklyHoursChange(Map<DayOfWeek, List<TimeRange>> hours) implements ProposedChange {

		@Override
		public boolean affects(PoolDaySchedule schedule) {
			return true;
		}

		@Override
		public UnaryOperator<ScheduleInputs> apply(LocalDate on) {
			return it -> it.weeklyHours(hours.getOrDefault(on.getDayOfWeek(), List.of()));
		}
	}
}
