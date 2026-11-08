package com.samdev.appointments.booking.application.port;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.domain.vo.OfferingTerms;
import com.samdev.appointments.branchmanagement.BranchDirectory.ScheduleSnapshot;
import com.samdev.appointments.branchmanagement.CalendarExceptionId;
import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Applies Branch management events to the projection. Every update is idempotent and keyed by the upstream ID; an
 * event no newer than the last one applied to the same row, or no newer than the last rebuild, is ignored. Updates
 * are serialised.
 */
public interface ProjectionUpdates {

	enum Result {

		APPLIED,

		/** Already applied, or older than what the projection holds. */
		IGNORED,

		/** The row the event changes is not in the projection yet; rebuild it. */
		MISSING
	}

	/**
	 * Replaces everything in the projection with the snapshot, as of {@code at}.
	 */
	void rebuild(ScheduleSnapshot snapshot, Instant at);

	Result weeklyHours(Map<DayOfWeek, List<TimeRange>> hours, Instant at);

	Result pool(CapacityPoolId poolId, int baseCapacity, Instant at);

	/**
	 * @param active {@code true} for a new offering, {@code null} to keep the current flag on a change
	 */
	Result offering(ServiceOfferingId offeringId, ServiceId serviceId, CapacityPoolId poolId, OfferingTerms terms,
			@Nullable Boolean active, Instant at);

	Result offeringWithdrawn(ServiceOfferingId offeringId, Instant at);

	Result exception(CalendarExceptionId exceptionId, LocalDate date, ExceptionType type, List<TimeRange> hours,
			Instant at);

	Result exceptionRemoved(CalendarExceptionId exceptionId, Instant at);

	Result override(CapacityOverrideId overrideId, CapacityPoolId poolId, LocalDate date, @Nullable TimeRange window,
			int capacity, Instant at);

	Result overrideRemoved(CapacityOverrideId overrideId, Instant at);
}
