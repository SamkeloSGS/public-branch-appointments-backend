package com.samdev.appointments.branchmanagement.application.command;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Branch manager. Replaces the whole weekly schedule; a day without ranges is closed.
 */
public record SetWeeklyHours(@Nullable Long expectedVersion,
		Map<DayOfWeek, List<TimeRange>> weeklyHours) {}
