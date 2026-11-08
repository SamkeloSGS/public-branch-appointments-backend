package com.samdev.appointments.branchmanagement.web.dto.request;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Partial update: absent fields keep their current value.
 */
public record ChangeOfferingRequest(@Nullable UUID poolId, @Nullable Integer durationMinutes,
		@Nullable Integer slotIntervalMinutes, @Nullable Integer minLeadTimeMinutes,
		@Nullable Integer maxAdvanceDays) {}
