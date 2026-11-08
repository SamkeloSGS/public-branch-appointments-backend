package com.samdev.appointments.booking.web.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * @param dates the dates in the range with at least one open time
 */
public record AvailableDaysResponse(LocalDate from, LocalDate to, List<LocalDate> dates) {}
