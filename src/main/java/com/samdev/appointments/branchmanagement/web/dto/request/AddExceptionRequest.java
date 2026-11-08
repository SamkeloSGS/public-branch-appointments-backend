package com.samdev.appointments.branchmanagement.web.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.web.dto.TimeRangeDto;

public record AddExceptionRequest(@NotNull LocalDate date, @NotNull ExceptionType type,
		@Nullable List<@Valid @NotNull TimeRangeDto> hours, @Nullable String reason) {}
