package com.samdev.appointments.booking.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;

/**
 * Branch staff, after check-in.
 */
public record CompleteAppointment(AppointmentId appointmentId, @Nullable Long expectedVersion) {}
