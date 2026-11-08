package com.samdev.appointments.branchmanagement.web.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/**
 * @param durationMinutes optional; defaults to the service's default duration
 */
public record OfferServiceRequest(@NotNull UUID serviceId, @NotNull UUID poolId, @Nullable Integer durationMinutes,
		@NotNull Integer slotIntervalMinutes, @NotNull Integer minLeadTimeMinutes,
		@NotNull Integer maxAdvanceDays) {}
