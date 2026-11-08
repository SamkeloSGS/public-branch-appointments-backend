package com.samdev.appointments.branchmanagement.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.OverrideReason;
import com.samdev.appointments.branchmanagement.web.dto.TimeRangeDto;

public record SetOverrideRequest(@NotNull UUID poolId, @NotNull LocalDate date, @Nullable @Valid TimeRangeDto window,
		@NotNull Integer capacity, @NotNull OverrideReason reason, @Nullable String note) {}
