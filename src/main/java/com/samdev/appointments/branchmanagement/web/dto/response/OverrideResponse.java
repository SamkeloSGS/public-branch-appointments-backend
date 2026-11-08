package com.samdev.appointments.branchmanagement.web.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.OverrideReason;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityOverride;
import com.samdev.appointments.branchmanagement.web.dto.TimeRangeDto;

public record OverrideResponse(UUID id, UUID poolId, LocalDate date, @Nullable TimeRangeDto window,
		int capacity, OverrideReason reason, @Nullable String note, long version) {

	public static OverrideResponse of(CapacityOverride override) {
		return new OverrideResponse(override.getId().value(),
				override.getCapacityPoolId().value(), override.getDate(),
				override.getWindow() == null ? null : TimeRangeDto.of(override.getWindow()),
				override.getCapacity(), override.getReason(), override.getNote(), override.getVersion());
	}
}
