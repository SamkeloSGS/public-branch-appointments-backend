package com.samdev.appointments.booking.web.dto.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.application.query.ImpactPreview.CalendarExceptionChange;
import com.samdev.appointments.booking.application.query.ImpactPreview.CapacityOverrideChange;
import com.samdev.appointments.booking.application.query.ImpactPreview.ProposedChange;
import com.samdev.appointments.booking.application.query.ImpactPreview.WeeklyHoursChange;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * A change Branch management is about to make, with the fields for its kind:
 * <ul>
 * <li>{@code CALENDAR_EXCEPTION}: date, exceptionType, and hours for {@code SPECIAL_HOURS}.</li>
 * <li>{@code CAPACITY_OVERRIDE}: poolId, date, capacity, and window (empty for the whole day).</li>
 * <li>{@code WEEKLY_HOURS}: weeklyHours.</li>
 * </ul>
 */
public record ImpactPreviewRequest(@NotNull Kind kind, @Nullable LocalDate date, @Nullable ExceptionType exceptionType,
		@Nullable List<TimeRange> hours, @Nullable UUID poolId, @Nullable TimeRange window, @Nullable Integer capacity,
		@Nullable Map<DayOfWeek, List<TimeRange>> weeklyHours) {

	public enum Kind {
		CALENDAR_EXCEPTION, CAPACITY_OVERRIDE, WEEKLY_HOURS
	}

	/**
	 * @throws IllegalArgumentException when a field the kind needs is missing
	 */
	public ProposedChange change() {
		return switch (kind) {
			case CALENDAR_EXCEPTION -> {
				require(date != null && exceptionType != null, "date and exceptionType");
				require(exceptionType == ExceptionType.CLOSED || (hours != null && !hours.isEmpty()), "hours");
				yield new CalendarExceptionChange(date, exceptionType, hours == null ? List.of() : hours);
			}
			case CAPACITY_OVERRIDE -> {
				require(date != null && poolId != null && capacity != null && capacity >= 0, "poolId, date and capacity");
				yield new CapacityOverrideChange(new CapacityPoolId(poolId), date, window, capacity);
			}
			case WEEKLY_HOURS -> {
				require(weeklyHours != null, "weeklyHours");
				yield new WeeklyHoursChange(weeklyHours);
			}
		};
	}

	private void require(boolean present, String fields) {
		if (!present) {
			throw new IllegalArgumentException(kind + " needs " + fields);
		}
	}
}
