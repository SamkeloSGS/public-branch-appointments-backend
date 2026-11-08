package com.samdev.appointments.booking.application.port;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import com.samdev.appointments.booking.domain.vo.OfferingTerms;
import com.samdev.appointments.booking.domain.vo.ScheduleInputs;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;

/**
 * Reads Booking's own copy of what it needs from Branch management.
 */
public interface ScheduleProjection {

	/**
	 * Whether the projection has been built from the branch's schedule snapshot.
	 */
	boolean isBuilt();

	/**
	 * The branch's time zone, from configuration.
	 */
	ZoneId timeZone();

	/**
	 * The offering of the service, active or withdrawn.
	 */
	Optional<OfferingView> offering(ServiceId serviceId);

	/**
	 * What the effective capacity rule needs for the pool on the date; {@link ScheduleInputs#unavailable()} while the
	 * projection is not built.
	 */
	ScheduleInputs inputs(CapacityPoolId poolId, LocalDate date);

	/**
	 * {@link #inputs(CapacityPoolId, LocalDate)} for every date from {@code from} to {@code to}, read in a fixed
	 * number of queries however long the range.
	 */
	Map<LocalDate, ScheduleInputs> inputs(CapacityPoolId poolId, LocalDate from, LocalDate to);

	record OfferingView(ServiceOfferingId id, ServiceId serviceId, CapacityPoolId poolId, OfferingTerms terms,
			boolean active) {}
}
