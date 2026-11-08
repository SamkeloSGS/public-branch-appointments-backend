package com.samdev.appointments.branchmanagement.web.dto.request;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.web.dto.TimeRangeDto;

public record WeeklyHoursRequest(@NotNull Map<DayOfWeek, List<@Valid @NotNull TimeRangeDto>> days) {

	public Map<DayOfWeek, List<TimeRange>> toWeeklyHours() {
		return days.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, it -> TimeRangeDto.toTimeRanges(it.getValue())));
	}
}
