package com.samdev.appointments.booking.application.command;

import java.time.LocalDate;

import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * System: rebuild the capacity profile of one pool day from the projection and apply it.
 */
public record RecomputeSchedule(CapacityPoolId poolId, LocalDate date) {}
