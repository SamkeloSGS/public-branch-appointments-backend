package com.samdev.appointments.branchmanagement.web.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.domain.entity.CalendarException;
import com.samdev.appointments.branchmanagement.web.dto.TimeRangeDto;

public record ExceptionResponse(UUID id, LocalDate date, ExceptionType type, List<TimeRangeDto> hours,
		@Nullable String reason, long version) {

	public static ExceptionResponse of(CalendarException exception) {
		return new ExceptionResponse(exception.getId().value(),
				exception.getDate(), exception.getType(), TimeRangeDto.of(exception.getHours()),
				exception.getReason(), exception.getVersion());
	}
}
