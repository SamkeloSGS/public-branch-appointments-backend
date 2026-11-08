package com.samdev.appointments.booking.web.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * @param start local date and time at the branch, such as {@code 2026-10-14T09:30}
 */
public record BookingRequest(@NotNull UUID serviceId, @NotNull LocalDateTime start) {}
