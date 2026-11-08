package com.samdev.appointments.booking.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;

/**
 * Branch staff, on the appointment date.
 */
public record CheckInAppointment(AppointmentId appointmentId, @Nullable Long expectedVersion) {}
