package com.samdev.appointments.branchmanagement.application.command;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.OverrideReason;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Branch manager. A {@code null} window means the whole day. Succeeds regardless of existing appointments.
 */
public record SetCapacityOverride(CapacityPoolId poolId, LocalDate date,
		@Nullable TimeRange window, int capacity, OverrideReason reason, @Nullable String note) {}
