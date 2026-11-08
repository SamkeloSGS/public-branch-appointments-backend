package com.samdev.appointments.branchmanagement.web.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.domain.vo.WeeklyHours;

public record TimeRangeDto(@NotNull LocalTime start, @NotNull LocalTime end) {

	public static TimeRangeDto of(TimeRange range) {
		return new TimeRangeDto(range.start(), range.end());
	}

	public static List<TimeRangeDto> of(List<TimeRange> ranges) {
		return ranges.stream().map(TimeRangeDto::of).toList();
	}

	/**
	 * Every day of the week mapped to its ranges; closed days map to an empty list.
	 */
	public static Map<DayOfWeek, List<TimeRangeDto>> ofWeek(WeeklyHours hours) {
		return hours.asMap().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, it -> of(it.getValue()), (a, b) -> a,
						() -> new EnumMap<>(DayOfWeek.class)));
	}

	public TimeRange toTimeRange() {
		return new TimeRange(start, end);
	}

	public static List<TimeRange> toTimeRanges(@Nullable List<TimeRangeDto> ranges) {
		return ranges == null ? List.of() : ranges.stream().map(TimeRangeDto::toTimeRange).toList();
	}
}
