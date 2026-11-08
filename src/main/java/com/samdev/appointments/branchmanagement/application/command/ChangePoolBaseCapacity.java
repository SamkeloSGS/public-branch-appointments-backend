package com.samdev.appointments.branchmanagement.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * Branch manager.
 */
public record ChangePoolBaseCapacity(CapacityPoolId poolId, @Nullable Long expectedVersion,
		int baseCapacity) {}
