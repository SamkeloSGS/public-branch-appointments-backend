package com.samdev.appointments.booking.web.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * @param openTimes local start times at the branch, in order
 */
public record AvailabilityResponse(LocalDate date, List<LocalTime> openTimes) {}
