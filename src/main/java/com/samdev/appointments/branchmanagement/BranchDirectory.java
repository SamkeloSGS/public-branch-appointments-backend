package com.samdev.appointments.branchmanagement;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Read-only query facade of the Branch management module. This is the only interface other modules may call;
 * commands are never exposed here.
 */
public interface BranchDirectory {

	/**
	 * The branch this application runs for, from configuration. Used by Booking and Notification.
	 */
	BranchProfile branch();

	/**
	 * Returns the service, active or retired. Used by Notification.
	 */
	Optional<ServiceSummary> findService(ServiceId serviceId);

	/**
	 * Returns the service category, active or retired.
	 */
	Optional<ServiceCategorySummary> findServiceCategory(ServiceCategoryId categoryId);

	/**
	 * Returns everything Booking needs to build or rebuild its read model: weekly hours, pools, offerings, and
	 * exceptions and overrides from today (in the branch's time zone) onwards.
	 */
	ScheduleSnapshot scheduleSnapshot();

	record ServiceSummary(ServiceId id, String name, ServiceCategoryId categoryId, CustomerSegment segment,
			@Nullable String description, int defaultDurationMinutes, boolean active) {}

	record ServiceCategorySummary(ServiceCategoryId id, String name, CustomerSegment segment,
			@Nullable String description, boolean active) {}

	record ScheduleSnapshot(ZoneId timeZone, Map<DayOfWeek, List<TimeRange>> weeklyHours, List<PoolSnapshot> pools,
			List<OfferingSnapshot> offerings, List<ExceptionSnapshot> calendarExceptions,
			List<OverrideSnapshot> capacityOverrides) {}

	record PoolSnapshot(CapacityPoolId id, String name, int baseCapacity) {}

	record OfferingSnapshot(ServiceOfferingId id, ServiceId serviceId, CapacityPoolId poolId, int durationMinutes,
			int slotIntervalMinutes, int minLeadTimeMinutes, int maxAdvanceDays, boolean active) {}

	record ExceptionSnapshot(CalendarExceptionId id, LocalDate date, ExceptionType type, List<TimeRange> hours) {}

	record OverrideSnapshot(CapacityOverrideId id, CapacityPoolId poolId, LocalDate date, @Nullable TimeRange window,
			int capacity, OverrideReason reason) {}
}
