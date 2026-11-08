package com.samdev.appointments.booking.web.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * @param start the new local date and time at the branch
 */
public record RescheduleRequest(@NotNull LocalDateTime start) {}
