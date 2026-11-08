package com.samdev.appointments.branchmanagement.application.command;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceId;

/**
 * Branch manager. A {@code null} duration uses the service's default duration.
 */
public record OfferService(ServiceId serviceId, CapacityPoolId poolId,
		@Nullable Duration duration, Duration slotInterval, Duration minLeadTime, int maxAdvanceDays) {}
