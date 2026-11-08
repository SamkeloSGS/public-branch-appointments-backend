package com.samdev.appointments.branchmanagement.application.command;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Branch manager. Succeeds regardless of existing appointments.
 */
public record AddCalendarException(LocalDate date, ExceptionType type, List<TimeRange> hours,
		@Nullable String reason) {}
