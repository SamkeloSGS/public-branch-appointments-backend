package com.samdev.appointments.branchmanagement.application.command;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;

/**
 * Branch manager.
 */
public record ChangeServiceOffering(ServiceOfferingId offeringId, @Nullable Long expectedVersion,
		CapacityPoolId poolId, Duration duration, Duration slotInterval, Duration minLeadTime, int maxAdvanceDays) {}
